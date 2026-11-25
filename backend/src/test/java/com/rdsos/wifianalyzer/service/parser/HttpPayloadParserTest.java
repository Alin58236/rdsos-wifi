package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HttpPayloadParserTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");

    // in managed-http.pcap: Ethernet 14 + IPv4 20 + TCP 32 (with options), so the payload starts at 66
    private static final int PAYLOAD_OFFSET = 66;

    @Test
    void givenTheHttpGet_ThenTheWholeRequestIsReadable(){

        HttpPayloadParser parser = new HttpPayloadParser();

        // packet 5: IPv4 total length 127 - IPv4 header 20 - TCP header 32 = 75 bytes of payload
        RawFrame frame = readFrameNo(MANAGED_HTTP, 5);

        String result = parser.parse(frame.getData(), PAYLOAD_OFFSET, 75);

        // \r and \n are kept, so the request shows on separate lines like in Wireshark
        assertThat(result).isEqualTo(
                "GET / HTTP/1.1\r\n" +
                "Host: neverssl.com\r\n" +
                "User-Agent: curl/8.5.0\r\n" +
                "Accept: */*\r\n" +
                "\r\n");
    }

    @Test
    void givenTheHttpResponse_ThenItStartsWithTheStatusLineAndContainsThePage(){

        HttpPayloadParser parser = new HttpPayloadParser();

        // packet 7: IPv4 total length 4313 - 20 - 32 = 4261 bytes of payload
        RawFrame frame = readFrameNo(MANAGED_HTTP, 7);

        String result = parser.parse(frame.getData(), PAYLOAD_OFFSET, 4261);

        assertThat(result).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(result).contains("<html");
        // one character per byte, even for the bytes that are replaced
        assertThat(result).hasSize(4261);
    }

    @Test
    void givenNonPrintableBytes_ThenTheyBecomeDots(){

        HttpPayloadParser parser = new HttpPayloadParser();

        // 'H', 0x00, 'i', 0xff, '\t', '\n'
        byte[] data = {0x48, 0x00, 0x69, (byte) 0xff, 0x09, 0x0a};

        String result = parser.parse(data, 0, data.length);

        assertThat(result).isEqualTo("H.i.\t\n");
    }

    @Test
    void givenAnEmptyPayload_ThenTheResultIsEmpty(){

        HttpPayloadParser parser = new HttpPayloadParser();

        // a SYN or a plain ACK carries no data
        byte[] data = {0x48, 0x49};

        assertThat(parser.parse(data, 2, 0)).isEmpty();
    }

    @Test
    void givenALengthPastTheEndOfTheData_ThenThrows(){

        HttpPayloadParser parser = new HttpPayloadParser();

        byte[] data = {0x48, 0x49, 0x4a};

        assertThatThrownBy(() -> parser.parse(data, 1, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenANegativeLength_ThenThrows(){

        HttpPayloadParser parser = new HttpPayloadParser();

        byte[] data = {0x48, 0x49, 0x4a};

        assertThatThrownBy(() -> parser.parse(data, 0, -1))
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
