package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.TcpHeader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TcpHeaderParserTest {

    private static final Path MANAGED_HTTP = Path.of("src/test/resources/fixtures/managed-http.pcap");

    // in managed-http.pcap: Ethernet (14 bytes) + IPv4 without options (20 bytes), so TCP starts at 34
    private static final int TCP_OFFSET = 34;

    private static final int LAPTOP_PORT = 39194;
    private static final int HTTP_PORT = 80;

    @Test
    void givenTheSyn_ThenEveryFieldIsRead(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // packet 2, the laptop opens the connection. Header: 99 1a 00 50 2b 9c d8 b8 00 00 00 00 a0 02 fa f0 42 8e 00 00 + 20 bytes of options
        RawFrame frame = readFrameNo(MANAGED_HTTP, 2);

        TcpHeader result = parser.parse(frame.getData(), TCP_OFFSET);

        TcpHeader expected = TcpHeader.builder()
                .sourcePort(LAPTOP_PORT)
                .destinationPort(HTTP_PORT)
                .sequenceNumber(731699384L)
                .acknowledgmentNumber(0)
                // a0 -> data offset 10 -> 40 bytes
                .headerLengthBytes(40)
                .nonceSum(false)
                .congestionWindowReduced(false)
                .ecnEcho(false)
                .urgent(false)
                .acknowledgment(false)
                .push(false)
                .reset(false)
                .synchronize(true)
                .finish(false)
                .receiveWindowSize(64240)
                .checksum(0x428e)
                .urgentPointer(0)
                // MSS 1460, SACK permitted, timestamps, NOP, window scale 10
                .options(new byte[]{
                        0x02, 0x04, 0x05, (byte) 0xb4,
                        0x04, 0x02,
                        0x08, 0x0a, (byte) 0xa7, (byte) 0x96, 0x64, (byte) 0xce, 0x00, 0x00, 0x00, 0x00,
                        0x01,
                        0x03, 0x03, 0x0a})
                .build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void givenTheSynAck_ThenItAcknowledgesTheSynSequencePlusOne(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // packet 3: the server answers the SYN
        RawFrame frame = readFrameNo(MANAGED_HTTP, 3);

        TcpHeader result = parser.parse(frame.getData(), TCP_OFFSET);

        assertThat(result.getSourcePort()).isEqualTo(HTTP_PORT);
        assertThat(result.getDestinationPort()).isEqualTo(LAPTOP_PORT);
        assertThat(result.isSynchronize()).isTrue();
        assertThat(result.isAcknowledgment()).isTrue();
        // the SYN had sequence 731699384, so the server expects 731699385 next
        assertThat(result.getAcknowledgmentNumber()).isEqualTo(731699385L);
    }

    @Test
    void givenTheHttpGet_ThenPushAndAckAreSetAndTheHeaderIs32Bytes(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // packet 5: "GET / HTTP/1.1"
        RawFrame frame = readFrameNo(MANAGED_HTTP, 5);

        TcpHeader result = parser.parse(frame.getData(), TCP_OFFSET);

        assertThat(result.isPush()).isTrue();
        assertThat(result.isAcknowledgment()).isTrue();
        assertThat(result.isSynchronize()).isFalse();
        // 80 -> data offset 8 -> 32 bytes: 20 fixed + 12 bytes of options (NOP, NOP, timestamps)
        assertThat(result.getHeaderLengthBytes()).isEqualTo(32);
        assertThat(result.getOptions()).hasSize(12);
    }

    @Test
    void givenTheFin_ThenFinishAndAckAreSet(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // packet 9: the laptop closes the connection
        RawFrame frame = readFrameNo(MANAGED_HTTP, 9);

        TcpHeader result = parser.parse(frame.getData(), TCP_OFFSET);

        assertThat(result.isFinish()).isTrue();
        assertThat(result.isAcknowledgment()).isTrue();
        assertThat(result.isPush()).isFalse();
    }

    @Test
    void givenAllNineFlags_ThenEachIsReadFromItsOwnBit(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // byte 12 = 0x51: data offset 5 (20 bytes) and NS (lowest bit); byte 13 = 0xff: the other 8 flags
        // sequence ff ff ff ff: the largest unsigned 32-bit value, which does not fit in a Java int
        byte[] data = {
                0x00, 0x50, 0x00, 0x51,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                0x00, 0x00, 0x00, 0x01,
                0x51, (byte) 0xff,
                0x01, 0x00,
                0x12, 0x34,
                0x00, 0x07
        };

        TcpHeader result = parser.parse(data, 0);

        assertThat(result.isNonceSum()).isTrue();
        assertThat(result.isCongestionWindowReduced()).isTrue();
        assertThat(result.isEcnEcho()).isTrue();
        assertThat(result.isUrgent()).isTrue();
        assertThat(result.isAcknowledgment()).isTrue();
        assertThat(result.isPush()).isTrue();
        assertThat(result.isReset()).isTrue();
        assertThat(result.isSynchronize()).isTrue();
        assertThat(result.isFinish()).isTrue();
        assertThat(result.getSequenceNumber()).isEqualTo(4294967295L);
        assertThat(result.getHeaderLengthBytes()).isEqualTo(20);
        assertThat(result.getOptions()).isEmpty();
        assertThat(result.getUrgentPointer()).isEqualTo(7);
    }

    @Test
    void givenLessThan20Bytes_ThenThrows(){

        TcpHeaderParser parser = new TcpHeaderParser();

        byte[] data = {0x00, 0x50, 0x00, 0x51, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00};

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenADataOffsetSmallerThan5_ThenThrows(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // byte 12 = 0x40: data offset 4 would be a 16-byte header, smaller than the fixed 20 bytes
        byte[] data = {
                0x00, 0x50, 0x00, 0x51, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x40, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenADataOffsetLongerThanTheData_ThenThrows(){

        TcpHeaderParser parser = new TcpHeaderParser();

        // byte 12 = 0x60: data offset 6 says 24 bytes, but only 20 are there
        byte[] data = {
                0x00, 0x50, 0x00, 0x51, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x60, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00
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
