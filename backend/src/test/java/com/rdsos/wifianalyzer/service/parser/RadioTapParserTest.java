package com.rdsos.wifianalyzer.service.parser;


import com.rdsos.wifianalyzer.model.RadioTapHeader;
import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RadioTapParserTest {



    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    @Test
    public void givenValidFileAndRawFrame_thenParseFirstRawFrameFromFile(){

        RadioTapParser parser = new RadioTapParser();
        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){

            RawFrame frame = source.next();

            RadioTapHeader result = parser.parse(frame.getData(),0);
            RadioTapHeader expected = new RadioTapHeader(38,true,2457,-90);

            assertThat(result).isEqualTo(expected);

        }
    }

    @Test
    void rejectsPresentWordsThatRunPastTheHeaderLength() {
        // length = 8 leaves room for one present word, but its bit 31 says another one follows
        byte[] data = {
                0x00, 0x00, 0x08, 0x00,
                0x00, 0x00, 0x00, (byte) 0x80,
                0x00, 0x00, 0x00, 0x00
        };

        assertThatThrownBy(() -> new RadioTapParser().parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void givenAFullFileWith50Beacons_thenReadAllOfThemCorrectly(){
        RadioTapParser parser = new RadioTapParser();
        int expectedFrames=50;
        int actualFrames=0;
        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){

            RawFrame frame=source.next();
            while(frame!=null){
                RadioTapHeader result = parser.parse(frame.getData(),0);
                assertThat(result.getFreqMHz()).isEqualTo(2457);
                assertThat(result.getSignalDBm()).isBetween(-100,-30);
                actualFrames++;
                frame=source.next();
            }
            assertThat(actualFrames).isEqualTo(expectedFrames);
        }
    }

    @Test
    void givenHeaderWithnoField_ThenNoDataIsReadAndDefaultFrameIsReturned(){
        RadioTapParser parser = new RadioTapParser();

        byte[] inputData = {0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};

            RadioTapHeader header = parser.parse(inputData,0);

            assertThat(header).isEqualTo(new RadioTapHeader(8,false,null,null));

    }

    @Test
    void givenChannelAfterAnOddPosition_thenSkipsThePaddingByte() {
        byte[] inputData = {0x00, 0x00, 0x0e, 0x00, 0x0a, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x99, 0x09, (byte) 0xa0, 0x00};

        RadioTapHeader header = new RadioTapParser().parse(inputData, 0);

        assertThat(header).isEqualTo(new RadioTapHeader(14, true, 2457, null));
    }

    @Test
    void givenVersionOtherThanZero_thenThrows() {
        byte[] inputData = {0x01, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> new RadioTapParser().parse(inputData, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }


}
