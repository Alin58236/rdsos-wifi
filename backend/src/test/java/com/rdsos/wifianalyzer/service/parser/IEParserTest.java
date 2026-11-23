package com.rdsos.wifianalyzer.service.parser;


import com.rdsos.wifianalyzer.model.Dot11Header;
import com.rdsos.wifianalyzer.model.IE;
import com.rdsos.wifianalyzer.model.RadioTapHeader;
import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IEParserTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    // the fixed part of a beacon body: timestamp (8) + beacon interval (2) + capability (2)
    private static final int BEACON_FIXED_FIELDS = 12;

    @Test
    void givenFirstBeacon_ThenAllIEsAreReadUntilTheFcs(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();
        InformationElementParser ieParser = new InformationElementParser();

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){

            RawFrame frame = source.next();
            byte[] data = frame.getData();

            RadioTapHeader radiotap = radioTapParser.parse(data,0);
            Dot11Header dot11 = dot11Parser.parse(data, radiotap.getLength());

            // 38 + 24 + 12 = 74, and the last 4 bytes are the FCS, so the IEs end at 378 - 4 = 374
            int offset = radiotap.getLength() + dot11.getHeaderLength() + BEACON_FIXED_FIELDS;
            int end = radiotap.isHasFrameCheckSequence() ? data.length - 4 : data.length;

            assertThat(offset).isEqualTo(74);
            assertThat(end).isEqualTo(374);

            List<IE> result = ieParser.parse(data, offset, end);

            assertThat(result).hasSize(17);
            assertThat(result.get(0)).isEqualTo(new IE(0,76,11));   // SSID "ArtemisZone"
            assertThat(result.get(2)).isEqualTo(new IE(3,99,1));    // DS Parameter Set, channel 10
            assertThat(result.get(6)).isEqualTo(new IE(48,117,24)); // RSN

            IE last = result.get(result.size() - 1);
            assertThat(last.getId()).isEqualTo(221);
            assertThat(last.getOffset() + last.getLength()).isEqualTo(374);
        }
    }

}
