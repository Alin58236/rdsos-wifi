package com.rdsos.wifianalyzer.service.sniffer;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.service.parser.CaptureSource;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PacketDecoderTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");
    private static final Path BEACONS = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");
    private static final Path WPA2_DATA = Path.of("src/test/resources/fixtures/wpa2-data.pcap");

    // ---------- managed mode (link type 1): Ethernet -> IPv4 -> TCP -> HTTP ----------

    @Test
    void givenTheManagedHttpGet_ThenEveryLayerIsDecoded(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(MANAGED_HTTP, 5);

        DecodedPacket result = decoder.decode(frame);

        // managed mode has no Radiotap and no 802.11 header, the kernel gives us Ethernet
        assertThat(result.getRadioTap()).isNull();
        assertThat(result.getDot11()).isNull();
        assertThat(result.getLlcSnap()).isNull();

        assertThat(result.getEthernet().getEtherType()).isEqualTo(0x0800);
        assertThat(result.getIpv4().getSourceAddress()).isEqualTo("192.168.0.165");
        assertThat(result.getIpv4().getDestinationAddress()).isEqualTo("34.223.124.45");
        assertThat(result.getTcp().getDestinationPort()).isEqualTo(80);
        assertThat(result.getHttpPayload()).startsWith("GET / HTTP/1.1\r\n");
        assertThat(result.getHttpPayload()).hasSize(75);
    }

    @Test
    void givenTheManagedHttpResponse_ThenHttpIsDecodedBecauseTheSourcePortIs80(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(MANAGED_HTTP, 7);

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.getTcp().getSourcePort()).isEqualTo(80);
        assertThat(result.getHttpPayload()).startsWith("HTTP/1.1 200 OK\r\n");
    }

    @Test
    void givenTheSyn_ThenThereIsTcpButNoHttp(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(MANAGED_HTTP, 2);

        DecodedPacket result = decoder.decode(frame);

        // port 80, but a SYN carries no payload
        assertThat(result.getTcp().isSynchronize()).isTrue();
        assertThat(result.getHttpPayload()).isNull();
    }

    @Test
    void givenAnIpv6Packet_ThenOnlyEthernetIsDecoded(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(MANAGED_HTTP, 1);

        DecodedPacket result = decoder.decode(frame);

        // IPv6 was dropped from the scope, so the decoding stops after Ethernet
        assertThat(result.getEthernet().getEtherType()).isEqualTo(0x86dd);
        assertThat(result.getIpv4()).isNull();
        assertThat(result.getTcp()).isNull();
    }

    @Test
    void givenAManagedFrame_ThenTheTimestampAndTheBytesAreKept(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(MANAGED_HTTP, 5);

        DecodedPacket result = decoder.decode(frame);

        // the UI needs them for the time column and the hex dump
        assertThat(result.getTimestampMicros()).isEqualTo(frame.getTimestampMicros());
        assertThat(result.getData()).isEqualTo(frame.getData());
    }

    @Test
    void givenATruncatedIpv4Header_ThenThrows(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame fullFrame = readFrameNo(MANAGED_HTTP, 5);

        // Ethernet (14) + only 10 bytes of the 20-byte IPv4 header
        byte[] cut = Arrays.copyOf(fullFrame.getData(), 24);
        RawFrame frame = new RawFrame(fullFrame.getTimestampMicros(), 1, cut.length, cut);

        assertThatThrownBy(() -> decoder.decode(frame))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- monitor mode (link type 127): Radiotap -> 802.11 -> ... ----------

    @Test
    void givenABeacon_ThenOnlyRadiotapAnd80211AreDecoded(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(BEACONS, 1);

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.getRadioTap()).isNotNull();
        assertThat(result.getDot11().getType()).isEqualTo(0);
        assertThat(result.getDot11().getSubtype()).isEqualTo(8);
        // a beacon is a management frame, it has no LLC/SNAP and no IP
        assertThat(result.isEncrypted()).isFalse();
        assertThat(result.getLlcSnap()).isNull();
        assertThat(result.getEthernet()).isNull();
    }

    @Test
    void givenABeacon_ThenTheBeaconBodyIsDecodedToo(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(BEACONS, 1);

        DecodedPacket result = decoder.decode(frame);

        // the AP registry and the packet list ("SSID=... CH=...") both use it
        assertThat(result.getBeacon().getSsid()).isEqualTo("HomeNetwork");
        assertThat(result.getBeacon().getChannel()).isEqualTo(10);
    }

    @Test
    void givenADataFrame_ThenThereIsNoBeacon(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = readFrameNo(WPA2_DATA, 2);

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.getBeacon()).isNull();
    }

    @Test
    void givenAProtectedQosDataFrame_ThenItIsMarkedEncryptedAndNotDecodedFurther(){

        PacketDecoder decoder = new PacketDecoder();
        // frame 2 in wpa2-data.pcap: QoS data with the Protected bit set
        RawFrame frame = readFrameNo(WPA2_DATA, 2);

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.getDot11().isProtectedFrame()).isTrue();
        assertThat(result.isEncrypted()).isTrue();
        // after the 802.11 header the bytes are WPA2 ciphertext, so nothing more can be read
        assertThat(result.getLlcSnap()).isNull();
        assertThat(result.getIpv4()).isNull();
    }

    @Test
    void givenANullDataFrame_ThenNothingAfter80211AndNoError(){

        PacketDecoder decoder = new PacketDecoder();
        // frame 1 in wpa2-data.pcap: Null data (subtype 4), a data frame without a body
        RawFrame frame = readFrameNo(WPA2_DATA, 1);

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.getDot11().getSubtype()).isEqualTo(4);
        assertThat(result.isEncrypted()).isFalse();
        assertThat(result.getLlcSnap()).isNull();
    }

    @Test
    void givenAnOpenNetworkDataFrame_ThenLlcSnapIpv4TcpAndHttpAreDecoded(){

        PacketDecoder decoder = new PacketDecoder();
        RawFrame frame = buildOpenNetworkFrameWithTheHttpGet();

        DecodedPacket result = decoder.decode(frame);

        assertThat(result.isEncrypted()).isFalse();
        assertThat(result.getDot11().getType()).isEqualTo(2);
        assertThat(result.getLlcSnap().getEtherType()).isEqualTo(0x0800);
        assertThat(result.getEthernet()).isNull();
        assertThat(result.getIpv4().getSourceAddress()).isEqualTo("192.168.0.165");
        assertThat(result.getTcp().getDestinationPort()).isEqualTo(80);
        assertThat(result.getHttpPayload()).startsWith("GET / HTTP/1.1\r\n");
    }

    @Test
    void givenAnUnknownLinkType_ThenThrows(){

        PacketDecoder decoder = new PacketDecoder();
        // 105 = 802.11 without Radiotap, we never open the interface like that
        RawFrame frame = new RawFrame(0, 105, 4, new byte[]{1, 2, 3, 4});

        assertThatThrownBy(() -> decoder.decode(frame))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- helpers ----------

    /*
     * We have no capture from an open network, so this builds the frame a monitor-mode card
     * would see there: the same IPv4 + TCP + HTTP bytes as the managed GET (packet 5),
     * but behind Radiotap + 802.11 + LLC/SNAP instead of Ethernet.
     */
    private RawFrame buildOpenNetworkFrameWithTheHttpGet(){

        // version 0, pad, length 8, present = 0 (no fields)
        byte[] radioTap = {0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] dot11 = {
                0x08, 0x01,                                  // data frame, ToDS, not protected
                0x00, 0x00,                                  // duration
                0x02, 0x00, 0x00, 0x00, 0x00, 0x01,          // addr1 = BSSID
                0x02, 0x00, 0x00, 0x00, 0x00, 0x02,          // addr2 = source
                0x02, 0x00, 0x00, 0x00, 0x00, 0x03,          // addr3 = destination
                0x00, 0x00                                   // sequence control
        };

        byte[] llcSnap = {(byte) 0xaa, (byte) 0xaa, 0x03, 0x00, 0x00, 0x00, 0x08, 0x00};

        // packet 5 without its 14-byte Ethernet header = IPv4 + TCP + HTTP
        byte[] managedGet = readFrameNo(MANAGED_HTTP, 5).getData();
        byte[] ipv4TcpHttp = Arrays.copyOfRange(managedGet, 14, managedGet.length);

        byte[] data = new byte[radioTap.length + dot11.length + llcSnap.length + ipv4TcpHttp.length];
        int position = 0;
        System.arraycopy(radioTap, 0, data, position, radioTap.length);
        position += radioTap.length;
        System.arraycopy(dot11, 0, data, position, dot11.length);
        position += dot11.length;
        System.arraycopy(llcSnap, 0, data, position, llcSnap.length);
        position += llcSnap.length;
        System.arraycopy(ipv4TcpHttp, 0, data, position, ipv4TcpHttp.length);

        return new RawFrame(0, 127, data.length, data);
    }

    // return frame no <number>, counting from 1
    private RawFrame readFrameNo(Path file, int number){
        try(CaptureSource source = new PcapFileReader(file)){
            RawFrame frame = null;
            for(int i = 0; i < number; i++){
                frame = source.next();
            }
            return frame;
        }
    }
}
