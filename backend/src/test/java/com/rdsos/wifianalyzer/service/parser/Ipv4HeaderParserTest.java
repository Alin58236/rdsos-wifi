package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.Ipv4Header;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Ipv4HeaderParserTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");

    // the IP addresses in managed-http.pcap
    private static final String LAPTOP = "192.168.0.165";
    private static final String NEVERSSL = "34.223.124.45";

    // in managed mode the IPv4 header starts right after the 14-byte Ethernet header
    private static final int ETHERNET_LENGTH = 14;

    @Test
    void givenTheSynPacket_ThenEveryFieldIsRead(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // packet 2, IPv4 header: 45 00 00 3c c4 47 40 00 40 06 16 1b c0 a8 00 a5 22 df 7c 2d
        RawFrame frame = readFrameNo(MANAGED_HTTP, 2);

        Ipv4Header result = parser.parse(frame.getData(), ETHERNET_LENGTH);

        Ipv4Header expected = Ipv4Header.builder()
                .version(4)
                .headerLengthBytes(20)
                .differentiatedServicesCodePoint(0)
                .explicitCongestionNotification(0)
                .totalLengthBytes(60)
                .identification(0xc447)
                .dontFragment(true)
                .moreFragments(false)
                .fragmentOffset(0)
                .timeToLive(64)
                .protocol(6)
                .headerChecksum(0x161b)
                .sourceAddress(LAPTOP)
                .destinationAddress(NEVERSSL)
                .options(new byte[0])
                .build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void givenTheHttpResponse_ThenItComesFromTheServer(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // packet 7: "HTTP/1.1 200 OK" from neverssl.com, 4313 bytes in total
        RawFrame frame = readFrameNo(MANAGED_HTTP, 7);

        Ipv4Header result = parser.parse(frame.getData(), ETHERNET_LENGTH);

        assertThat(result.getSourceAddress()).isEqualTo(NEVERSSL);
        assertThat(result.getDestinationAddress()).isEqualTo(LAPTOP);
        assertThat(result.getTotalLengthBytes()).isEqualTo(4313);
        assertThat(result.getTimeToLive()).isEqualTo(236);
    }

    @Test
    void givenDscpEcnAndAFragment_ThenTheBitsAreSplitCorrectly(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // byte 1 = 0xb9 = 1011 1001: DSCP 46 (101110, "expedited forwarding") and ECN 1 (01)
        // bytes 6-7 = 0x20b9: More Fragments set (0x2000), fragment offset 185 (0x00b9)
        byte[] data = {
                0x45, (byte) 0xb9, 0x00, 0x14,
                0x12, 0x34, 0x20, (byte) 0xb9,
                0x40, 0x11, 0x00, 0x00,
                0x0a, 0x00, 0x00, 0x01,
                0x0a, 0x00, 0x00, 0x02
        };

        Ipv4Header result = parser.parse(data, 0);

        assertThat(result.getDifferentiatedServicesCodePoint()).isEqualTo(46);
        assertThat(result.getExplicitCongestionNotification()).isEqualTo(1);
        assertThat(result.isDontFragment()).isFalse();
        assertThat(result.isMoreFragments()).isTrue();
        assertThat(result.getFragmentOffset()).isEqualTo(185);
        assertThat(result.getProtocol()).isEqualTo(17);
        assertThat(result.getSourceAddress()).isEqualTo("10.0.0.1");
        assertThat(result.getDestinationAddress()).isEqualTo("10.0.0.2");
    }

    @Test
    void givenAHeaderWithOptions_ThenTheOptionsAreTheBytesAfter20(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // first byte 0x46: IHL = 6, so the header has 24 bytes; the last 4 are options (3 x NOP + End)
        byte[] data = {
                0x46, 0x00, 0x00, 0x18,
                0x00, 0x01, 0x40, 0x00,
                0x40, 0x06, 0x00, 0x00,
                (byte) 0xc0, (byte) 0xa8, 0x00, 0x01,
                (byte) 0xc0, (byte) 0xa8, 0x00, 0x02,
                0x01, 0x01, 0x01, 0x00
        };

        Ipv4Header result = parser.parse(data, 0);

        assertThat(result.getHeaderLengthBytes()).isEqualTo(24);
        assertThat(result.getOptions()).containsExactly(0x01, 0x01, 0x01, 0x00);
    }

    @Test
    void givenAVersionOtherThan4_ThenThrows(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // first byte 0x65: version 6
        byte[] data = {
                0x65, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00, 0x40, 0x06,
                0x00, 0x00, 0x0a, 0x00, 0x00, 0x01, 0x0a, 0x00, 0x00, 0x02
        };

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenLessThan20Bytes_ThenThrows(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        byte[] data = {0x45, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00, 0x40, 0x06};

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnIhlLongerThanTheData_ThenThrows(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // IHL = 6 says 24 bytes, but only 20 are there
        byte[] data = {
                0x46, 0x00, 0x00, 0x18, 0x00, 0x00, 0x00, 0x00, 0x40, 0x06,
                0x00, 0x00, 0x0a, 0x00, 0x00, 0x01, 0x0a, 0x00, 0x00, 0x02
        };

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnIhlSmallerThan5_ThenThrows(){

        Ipv4HeaderParser parser = new Ipv4HeaderParser();

        // IHL = 4 would be a 16-byte header, smaller than the fixed 20 bytes
        byte[] data = {
                0x44, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00, 0x40, 0x06,
                0x00, 0x00, 0x0a, 0x00, 0x00, 0x01, 0x0a, 0x00, 0x00, 0x02
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
