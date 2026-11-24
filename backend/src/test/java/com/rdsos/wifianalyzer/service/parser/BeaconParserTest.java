package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class BeaconParserTest {


    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    @Test
    void givenFirstBeacon_ThenSsidChannelAndPrivacyAreRead(){

        BeaconParser beaconParser = new BeaconParser();
        Dot11Parser dot11Parser = new Dot11Parser();
        RadioTapParser radioTapParser = new RadioTapParser();

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){

            RawFrame frame = source.next();
            byte[] data = frame.getData();

            RadioTapHeader radioTap = radioTapParser.parse(data,0);
            Dot11Header dot11 = dot11Parser.parse(data,radioTap.getLength());

            int offset = radioTap.getLength() + dot11.getHeaderLength();
            int end = radioTap.isHasFrameCheckSequence() ? data.length - 4 : data.length;

            Beacon result = beaconParser.parse(data,offset,end);

            assertThat(result.getBeaconInterval()).isEqualTo(100);
            assertThat(result.isPrivacy()).isTrue();
            assertThat(result.getSsid()).isEqualTo("ArtemisZone");
            assertThat(result.getChannel()).isEqualTo(10);
            assertThat(result.getIes()).hasSize(17);
        }

    }

    @Test
    void givenHiddenSsid_ThenSsidIsEmptyAndChannelIsStillRead(){

        BeaconParser beaconParser = new BeaconParser();

        byte[] data = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0x64, 0x00,
                0x01, 0x00,
                0x00, 0x00,
                0x03, 0x01, 0x06
        };

        Beacon result = beaconParser.parse(data, 0, data.length);

        assertThat(result.getSsid()).isEqualTo("");
        assertThat(result.getChannel()).isEqualTo(6);
        assertThat(result.isPrivacy()).isFalse();
    }

    @Test
    void givenNoDsChannel_ThenChannelComesFromHtOperation(){

        BeaconParser beaconParser = new BeaconParser();

        byte[] data = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0x64, 0x00,
                0x01, 0x00,
                0x00, 0x04, 0x74, 0x65, 0x73, 0x74,
                0x3d, 0x01, 0x0b
        };

        Beacon result = beaconParser.parse(data, 0, data.length);

        assertThat(result.getSsid()).isEqualTo("test");
        assertThat(result.getChannel()).isEqualTo(11);
    }

    @Test
    void givenNoChannelIe_ThenChannelIsNull(){

        BeaconParser beaconParser = new BeaconParser();

        byte[] data = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0x64, 0x00,
                0x01, 0x00,
                0x00, 0x04, 0x74, 0x65, 0x73, 0x74
        };

        Beacon result = beaconParser.parse(data, 0, data.length);

        assertThat(result.getChannel()).isNull();
    }


}
