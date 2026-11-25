package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.analyzer.Dot11Header;
import com.rdsos.wifianalyzer.model.analyzer.RadioTapHeader;
import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Dot11ParserTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");
    private static final Path WPA2_DATA = Path.of("src/test/resources/fixtures/wpa2-data.pcap");

    // the MACs that show up in wpa2-data.pcap
    private static final String ROUTER = "50:d4:f7:03:12:f3"; //my router BSSID
    private static final String ROUTER_LAN = "50:d4:f7:03:12:f4"; //the router's LAN side
    private static final String IPHONE = "ae:80:66:e8:2f:e3";//random MAC of the phone


    @Test
    void givenValidRawFrame_ThenReturnValidDot11Header(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();
        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){

            RawFrame frame = source.next();

            RadioTapHeader radiotap = radioTapParser.parse(frame.getData(),0);

            Dot11Header result = dot11Parser.parse(frame.getData(),0 + radiotap.getLength());

            Dot11Header expectedResult = new Dot11Header(0,8,false,false,false, List.of("ff:ff:ff:ff:ff:ff","50:d4:f7:03:12:f3","50:d4:f7:03:12:f3"),"50:d4:f7:03:12:f3","50:d4:f7:03:12:f3","ff:ff:ff:ff:ff:ff",24);

            assertThat(result).isEqualTo(expectedResult);
        }

    }

    @Test
    void givenNullDataFrameToTheAp_ThenAddr1IsTheBssid(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();

        //frame 1: Null Data (type 2, subtype 4), ToDS = 1, no QoS
        RawFrame frame = readFrameNo(WPA2_DATA, 1);

        RadioTapHeader radiotap = radioTapParser.parse(frame.getData(),0);

        Dot11Header result = dot11Parser.parse(frame.getData(),0 + radiotap.getLength());

        Dot11Header expectedResult = new Dot11Header(2,4,true,false,false, List.of(ROUTER,IPHONE,ROUTER),ROUTER,IPHONE,ROUTER,24);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenQosDataFrameToTheAp_ThenHeaderHasTwoExtraBytes(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();

        //frame 2: QoS Data (type 2, subtype 8), ToDS = 1, Protected = 1
        RawFrame frame = readFrameNo(WPA2_DATA, 2);

        RadioTapHeader radiotap = radioTapParser.parse(frame.getData(),0);

        Dot11Header result = dot11Parser.parse(frame.getData(),0 + radiotap.getLength());

        Dot11Header expectedResult = new Dot11Header(2,8,true,false,true, List.of(ROUTER,IPHONE,ROUTER_LAN),ROUTER,IPHONE,ROUTER_LAN,26);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenQosDataFrameFromTheAp_ThenAddr2IsTheBssid(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();

        //frame 4: QoS Data (type 2, subtype 8), FromDS = 1, Protected = 1
        RawFrame frame = readFrameNo(WPA2_DATA, 4);

        RadioTapHeader radiotap = radioTapParser.parse(frame.getData(),0);

        Dot11Header result = dot11Parser.parse(frame.getData(),0 + radiotap.getLength());

        Dot11Header expectedResult = new Dot11Header(2,8,false,true,true, List.of(IPHONE,ROUTER,ROUTER_LAN),ROUTER,ROUTER_LAN,IPHONE,26);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenAckFrame_ThenOnlyTheReceiverAddressIsRead(){

        Dot11Parser dot11Parser = new Dot11Parser();

        // ACK: 10 bytes, FC = d4 00 (type 1, subtype 13), duration, RA = the phone
        byte[] data = {
                (byte) 0xd4, 0x00,
                0x00, 0x00,
                (byte) 0xae, (byte) 0x80, 0x66, (byte) 0xe8, 0x2f, (byte) 0xe3
        };

        Dot11Header result = dot11Parser.parse(data, 0);

        Dot11Header expectedResult = new Dot11Header(1,13,false,false,false, List.of(IPHONE),null,null,IPHONE,10);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenRtsFrame_ThenReceiverAndTransmitterAreRead(){

        Dot11Parser dot11Parser = new Dot11Parser();

        // RTS: 16 bytes, FC = b4 00 (type 1, subtype 11), duration, RA = router, TA = the phone
        byte[] data = {
                (byte) 0xb4, 0x00,
                0x2c, 0x01,
                0x50, (byte) 0xd4, (byte) 0xf7, 0x03, 0x12, (byte) 0xf3,
                (byte) 0xae, (byte) 0x80, 0x66, (byte) 0xe8, 0x2f, (byte) 0xe3
        };

        Dot11Header result = dot11Parser.parse(data, 0);

        Dot11Header expectedResult = new Dot11Header(1,11,false,false,false, List.of(ROUTER,IPHONE),null,IPHONE,ROUTER,16);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenBlockAckFrame_ThenHeaderIs16EvenIfTheFrameIsLonger(){

        Dot11Parser dot11Parser = new Dot11Parser();

        // Block Ack: FC = 94 00 (type 1, subtype 9), RA = the phone, TA = router,
        // then 12 bytes of body (BA control, starting sequence, bitmap) that are not part of the header
        byte[] data = {
                (byte) 0x94, 0x00,
                0x00, 0x00,
                (byte) 0xae, (byte) 0x80, 0x66, (byte) 0xe8, 0x2f, (byte) 0xe3,
                0x50, (byte) 0xd4, (byte) 0xf7, 0x03, 0x12, (byte) 0xf3,
                0x05, 0x00, (byte) 0xa0, 0x36,
                (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        Dot11Header result = dot11Parser.parse(data, 0);

        Dot11Header expectedResult = new Dot11Header(1,9,false,false,false, List.of(IPHONE,ROUTER),null,ROUTER,IPHONE,16);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenFrameShorterThanAnAck_ThenThrows(){

        Dot11Parser dot11Parser = new Dot11Parser();

        byte[] data = {(byte) 0xd4, 0x00, 0x00, 0x00, (byte) 0xae};

        assertThatThrownBy(() -> dot11Parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // return frame no <number>
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
