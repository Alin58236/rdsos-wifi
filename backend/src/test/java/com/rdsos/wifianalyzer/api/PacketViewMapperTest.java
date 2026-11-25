package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.PacketDetail;
import com.rdsos.wifianalyzer.api.dto.PacketField;
import com.rdsos.wifianalyzer.api.dto.PacketLayer;
import com.rdsos.wifianalyzer.api.dto.PacketSummary;
import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import com.rdsos.wifianalyzer.service.parser.CaptureSource;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import com.rdsos.wifianalyzer.service.sniffer.PacketDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class PacketViewMapperTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");
    private static final Path BEACONS = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");
    private static final Path WPA2_DATA = Path.of("src/test/resources/fixtures/wpa2-data.pcap");

    private static final String ROUTER_AP = "50:d4:f7:03:12:f3";
    private static final String ROUTER_LAN = "50:d4:f7:03:12:f4";
    private static final String IPHONE = "ae:80:66:e8:2f:e3";
    private static final String LAPTOP = "80:91:33:50:03:49";

    // ================= PacketSummary: one row of the list =================

    @Test
    void givenABeacon_ThenTheSummaryShowsSsidAndChannel(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(7, BEACONS, 1));

        assertThat(result.getId()).isEqualTo(7);
        assertThat(result.getLength()).isEqualTo(378);
        assertThat(result.getProtocol()).isEqualTo("802.11 Beacon");
        assertThat(result.getSource()).isEqualTo(ROUTER_AP);
        assertThat(result.getDestination()).isEqualTo("ff:ff:ff:ff:ff:ff");
        assertThat(result.getInfo()).isEqualTo("SSID=HomeNetwork CH=10");
    }

    @Test
    void givenTheManagedHttpGet_ThenTheSummaryShowsIpAddressesAndTheRequestLine(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, MANAGED_HTTP, 5));

        assertThat(result.getProtocol()).isEqualTo("HTTP");
        // there is an IP layer, so IP addresses instead of MACs
        assertThat(result.getSource()).isEqualTo("192.168.0.165");
        assertThat(result.getDestination()).isEqualTo("34.223.124.45");
        // only the first line of the payload
        assertThat(result.getInfo()).isEqualTo("GET / HTTP/1.1");
    }

    @Test
    void givenTheSyn_ThenTheInfoShowsPortsAndFlags(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, MANAGED_HTTP, 2));

        assertThat(result.getProtocol()).isEqualTo("TCP");
        assertThat(result.getInfo()).isEqualTo("39194 → 80 [SYN]");
    }

    @Test
    void givenTheSynAck_ThenTheFlagsAreInWiresharkOrder(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, MANAGED_HTTP, 3));

        // Wireshark prints [SYN, ACK], [PSH, ACK], [FIN, ACK]
        assertThat(result.getInfo()).isEqualTo("80 → 39194 [SYN, ACK]");
    }

    @Test
    void givenAnEncryptedDataFrame_ThenTheSummarySaysEncrypted(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, WPA2_DATA, 2));

        assertThat(result.getProtocol()).isEqualTo("Encrypted (WPA2)");
        // no IP layer, so the 802.11 source and destination
        assertThat(result.getSource()).isEqualTo(IPHONE);
        assertThat(result.getDestination()).isEqualTo(ROUTER_LAN);
        assertThat(result.getInfo()).isEqualTo("Protected data");
    }

    @Test
    void givenANullDataFrame_ThenTheProtocolIsTheSubtypeName(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, WPA2_DATA, 1));

        assertThat(result.getProtocol()).isEqualTo("802.11 Null");
        assertThat(result.getSource()).isEqualTo(IPHONE);
    }

    @Test
    void givenAnIpv6Packet_ThenTheSummaryShowsEthernetAndTheEtherType(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketSummary result = mapper.toSummary(storedPacket(1, MANAGED_HTTP, 1));

        assertThat(result.getProtocol()).isEqualTo("Ethernet");
        assertThat(result.getSource()).isEqualTo(LAPTOP);
        assertThat(result.getDestination()).isEqualTo(ROUTER_LAN);
        assertThat(result.getInfo()).isEqualTo("EtherType 0x86dd");
    }

    // ================= PacketDetail: the layer tree and the hex dump =================

    @Test
    void givenABeacon_ThenTheDetailHasRadiotap80211AndTheBeaconBody(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, BEACONS, 1));

        assertThat(result.getLayers()).extracting(PacketLayer::getName)
                .containsExactly("Radiotap", "802.11 Beacon", "Beacon body");
        // Radiotap is 38 bytes in this capture, the 802.11 management header 24
        assertThat(result.getLayers()).extracting(PacketLayer::getOffset).containsExactly(0, 38, 62);
        assertThat(result.getLayers().get(0).getLength()).isEqualTo(38);
        assertThat(result.getLayers().get(1).getLength()).isEqualTo(24);

        PacketLayer dot11 = result.getLayers().get(1);
        assertThat(fieldValue(dot11, "Type / subtype")).isEqualTo("Management (0) / Beacon (8)");
        assertThat(fieldValue(dot11, "Source")).isEqualTo(ROUTER_AP);
        assertThat(fieldValue(dot11, "BSSID")).isEqualTo(ROUTER_AP);

        PacketLayer body = result.getLayers().get(2);
        assertThat(fieldValue(body, "SSID")).isEqualTo("HomeNetwork");
        assertThat(fieldValue(body, "Channel")).isEqualTo("10");
    }

    @Test
    void givenABeacon_ThenTheRadiotapFieldsHaveUnits(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, BEACONS, 1));

        PacketLayer radioTap = result.getLayers().get(0);
        assertThat(fieldValue(radioTap, "Header length")).isEqualTo("38 bytes");
        assertThat(fieldValue(radioTap, "Frequency")).endsWith(" MHz");
        assertThat(fieldValue(radioTap, "Signal")).endsWith(" dBm");
    }

    @Test
    void givenTheManagedHttpGet_ThenTheLayersAndTheirBytesAreRight(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, MANAGED_HTTP, 5));

        // managed mode: no Radiotap, no 802.11, the first layer says where it came from
        assertThat(result.getLayers()).extracting(PacketLayer::getName)
                .containsExactly("Ethernet (managed mode)", "IPv4", "TCP", "HTTP");
        assertThat(result.getLayers()).extracting(PacketLayer::getOffset).containsExactly(0, 14, 34, 66);
        assertThat(result.getLayers()).extracting(PacketLayer::getLength).containsExactly(14, 20, 32, 75);

        assertThat(fieldValue(result.getLayers().get(0), "EtherType")).isEqualTo("0x0800 (IPv4)");
        assertThat(fieldValue(result.getLayers().get(3), "Payload")).startsWith("GET / HTTP/1.1");
    }

    @Test
    void givenTheManagedHttpGet_ThenEveryIpv4FieldIsShown(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, MANAGED_HTTP, 5));
        PacketLayer ipv4 = result.getLayers().get(1);

        // the requirement is "all IPv4 header fields", in the order they are on the wire
        assertThat(ipv4.getFields()).extracting(PacketField::getName).containsExactly(
                "Version", "Header length", "DSCP", "ECN", "Total length", "Identification",
                "Don't fragment", "More fragments", "Fragment offset", "Time to live", "Protocol",
                "Header checksum", "Source", "Destination", "Options");

        assertThat(fieldValue(ipv4, "Version")).isEqualTo("4");
        assertThat(fieldValue(ipv4, "Header length")).isEqualTo("20 bytes");
        assertThat(fieldValue(ipv4, "Total length")).isEqualTo("127");
        assertThat(fieldValue(ipv4, "Identification")).isEqualTo("0xc449 (50249)");
        assertThat(fieldValue(ipv4, "Don't fragment")).isEqualTo("yes");
        assertThat(fieldValue(ipv4, "More fragments")).isEqualTo("no");
        assertThat(fieldValue(ipv4, "Time to live")).isEqualTo("64");
        assertThat(fieldValue(ipv4, "Protocol")).isEqualTo("6 (TCP)");
        assertThat(fieldValue(ipv4, "Header checksum")).isEqualTo("0x15d6");
        assertThat(fieldValue(ipv4, "Source")).isEqualTo("192.168.0.165");
        assertThat(fieldValue(ipv4, "Options")).isEqualTo("none");
    }

    @Test
    void givenTheManagedHttpGet_ThenEveryTcpFieldIsShown(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, MANAGED_HTTP, 5));
        PacketLayer tcp = result.getLayers().get(2);

        assertThat(tcp.getFields()).extracting(PacketField::getName).containsExactly(
                "Source port", "Destination port", "Sequence number", "Acknowledgment number",
                "Header length", "Flags", "Window size", "Checksum", "Urgent pointer", "Options");

        assertThat(fieldValue(tcp, "Source port")).isEqualTo("39194");
        assertThat(fieldValue(tcp, "Destination port")).isEqualTo("80");
        assertThat(fieldValue(tcp, "Sequence number")).isEqualTo("731699385");
        assertThat(fieldValue(tcp, "Acknowledgment number")).isEqualTo("993619364");
        assertThat(fieldValue(tcp, "Header length")).isEqualTo("32 bytes");
        assertThat(fieldValue(tcp, "Flags")).isEqualTo("PSH, ACK");
        assertThat(fieldValue(tcp, "Window size")).isEqualTo("63");
        assertThat(fieldValue(tcp, "Checksum")).isEqualTo("0x243e");
        assertThat(fieldValue(tcp, "Urgent pointer")).isEqualTo("0");
        // options are not decoded (dropped from the scope), shown as raw hex
        assertThat(fieldValue(tcp, "Options")).isEqualTo("0101080aa79665b1a7439af8");
    }

    @Test
    void givenAnEncryptedDataFrame_ThenTheLastLayerIsEncryptedWithNoFields(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, WPA2_DATA, 2));

        assertThat(result.getLayers()).extracting(PacketLayer::getName)
                .containsExactly("Radiotap", "802.11 QoS Data", "Encrypted (WPA2)");

        PacketLayer encrypted = result.getLayers().get(2);
        // Radiotap 41 + QoS data header 26
        assertThat(encrypted.getOffset()).isEqualTo(67);
        assertThat(encrypted.getFields()).isEmpty();
    }

    @Test
    void givenAnOpenNetworkDataFrame_ThenLlcSnapComesBeforeIpv4(){

        PacketViewMapper mapper = new PacketViewMapper();
        RawFrame frame = buildOpenNetworkFrameWithTheHttpGet();
        StoredPacket stored = new StoredPacket(1, new PacketDecoder().decode(frame));

        PacketDetail result = mapper.toDetail(stored);

        assertThat(result.getLayers()).extracting(PacketLayer::getName)
                .containsExactly("Radiotap", "802.11 Data", "LLC/SNAP", "IPv4", "TCP", "HTTP");
        // Radiotap 8, 802.11 data 24, LLC/SNAP 8, IPv4 20, TCP 32
        assertThat(result.getLayers()).extracting(PacketLayer::getOffset).containsExactly(0, 8, 32, 40, 60, 92);
        assertThat(fieldValue(result.getLayers().get(2), "EtherType")).isEqualTo("0x0800 (IPv4)");
    }

    @Test
    void givenAPacket_ThenTheHexIsTheWholeFrameInLowerCase(){

        PacketViewMapper mapper = new PacketViewMapper();

        PacketDetail result = mapper.toDetail(storedPacket(1, BEACONS, 1));

        // 2 hex characters per byte, no separators
        assertThat(result.getHex()).hasSize(378 * 2);
        assertThat(result.getHex()).startsWith("000026002f4000a0");
    }

    // ================= helpers =================

    private StoredPacket storedPacket(long id, Path file, int frameNumber){
        RawFrame frame = readFrameNo(file, frameNumber);
        return new StoredPacket(id, new PacketDecoder().decode(frame));
    }

    // the value of the field with this name, or null when the layer doesn't have it
    private String fieldValue(PacketLayer layer, String fieldName){
        for(PacketField field : layer.getFields()){
            if(field.getName().equals(fieldName)){
                return field.getValue();
            }
        }
        return null;
    }

    // same frame as in PacketDecoderTest: the managed GET behind Radiotap + 802.11 + LLC/SNAP
    private RawFrame buildOpenNetworkFrameWithTheHttpGet(){

        byte[] radioTap = {0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] dot11 = {
                0x08, 0x01, 0x00, 0x00,
                0x02, 0x00, 0x00, 0x00, 0x00, 0x01,
                0x02, 0x00, 0x00, 0x00, 0x00, 0x02,
                0x02, 0x00, 0x00, 0x00, 0x00, 0x03,
                0x00, 0x00
        };
        byte[] llcSnap = {(byte) 0xaa, (byte) 0xaa, 0x03, 0x00, 0x00, 0x00, 0x08, 0x00};

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
