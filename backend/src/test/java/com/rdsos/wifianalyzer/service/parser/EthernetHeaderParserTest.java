package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.EthernetHeader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EthernetHeaderParserTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");

    // the MACs that show up in managed-http.pcap
    private static final String ROUTER_LAN = "50:d4:f7:03:12:f4";
    private static final String LAPTOP = "80:91:33:50:03:49";

    @Test
    void givenTheSynPacket_ThenTheHeaderIsReadAndIpv4Follows(){

        EthernetHeaderParser parser = new EthernetHeaderParser();

        // packet 2: the laptop opens the TCP connection to neverssl.com, over IPv4
        RawFrame frame = readFrameNo(MANAGED_HTTP, 2);

        EthernetHeader result = parser.parse(frame.getData(), 0);

        EthernetHeader expected = EthernetHeader.builder()
                .destinationMac(ROUTER_LAN)
                .sourceMac(LAPTOP)
                .etherType(0x0800)
                .headerLengthBytes(14)
                .build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void givenAnIpv6Packet_ThenTheEtherTypeIs86dd(){

        EthernetHeaderParser parser = new EthernetHeaderParser();

        // packet 1: curl first tried IPv6
        RawFrame frame = readFrameNo(MANAGED_HTTP, 1);

        EthernetHeader result = parser.parse(frame.getData(), 0);

        assertThat(result.getEtherType()).isEqualTo(0x86DD);
        assertThat(result.getHeaderLengthBytes()).isEqualTo(14);
    }

    @Test
    void givenAVlanTag_ThenTheRealEtherTypeIsAfterTheTag(){

        EthernetHeaderParser parser = new EthernetHeaderParser();

        // destination MAC, source MAC, then 81 00 (VLAN tag), 00 0a (VLAN id 10), then the real EtherType 08 00
        byte[] data = {
                0x50, (byte) 0xd4, (byte) 0xf7, 0x03, 0x12, (byte) 0xf4,
                (byte) 0x80, (byte) 0x91, 0x33, 0x50, 0x03, 0x49,
                (byte) 0x81, 0x00,
                0x00, 0x0a,
                0x08, 0x00
        };

        EthernetHeader result = parser.parse(data, 0);

        assertThat(result.getEtherType()).isEqualTo(0x0800);
        assertThat(result.getHeaderLengthBytes()).isEqualTo(18);
    }

    @Test
    void givenLessThan14Bytes_ThenThrows(){

        EthernetHeaderParser parser = new EthernetHeaderParser();

        byte[] data = {0x50, (byte) 0xd4, (byte) 0xf7, 0x03, 0x12, (byte) 0xf4, (byte) 0x80, (byte) 0x91, 0x33, 0x50};

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAVlanTagWithoutRoomForTheRealEtherType_ThenThrows(){

        EthernetHeaderParser parser = new EthernetHeaderParser();

        // 14 bytes that say "VLAN tag follows", but the tag and the real EtherType are missing
        byte[] data = {
                0x50, (byte) 0xd4, (byte) 0xf7, 0x03, 0x12, (byte) 0xf4,
                (byte) 0x80, (byte) 0x91, 0x33, 0x50, 0x03, 0x49,
                (byte) 0x81, 0x00
        };

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
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
