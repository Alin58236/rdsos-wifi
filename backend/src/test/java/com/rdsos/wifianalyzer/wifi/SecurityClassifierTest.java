package com.rdsos.wifianalyzer.wifi;

import com.rdsos.wifianalyzer.model.analyzer.Beacon;
import com.rdsos.wifianalyzer.model.analyzer.Dot11Header;
import com.rdsos.wifianalyzer.model.analyzer.RadioTapHeader;
import com.rdsos.wifianalyzer.service.parser.*;
import com.rdsos.wifianalyzer.util.SecurityClassifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SecurityClassifierTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    @Test
    void givenFirstBeacon_ThenSecurityIsWpaWpa2Psk(){

        RadioTapParser radioTapParser = new RadioTapParser();
        Dot11Parser dot11Parser = new Dot11Parser();
        BeaconParser beaconParser = new BeaconParser();
        SecurityClassifier classifier = new SecurityClassifier();

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            byte[] data = source.next().getData();

            RadioTapHeader radioTap = radioTapParser.parse(data, 0);
            Dot11Header dot11 = dot11Parser.parse(data, radioTap.getLength());
            int offset = radioTap.getLength() + dot11.getHeaderLength();
            int end = radioTap.isHasFrameCheckSequence() ? data.length - 4 : data.length;
            Beacon beacon = beaconParser.parse(data, offset, end);

            assertThat(classifier.classify(data, beacon)).isEqualTo("WPA/WPA2-PSK");
        }
    }

    @Test
    void givenNoPrivacyAndNoSecurityIe_ThenNetworkIsOpen(){

        BeaconParser beaconParser = new BeaconParser();
        SecurityClassifier classifier = new SecurityClassifier();

        // 12 fixed bytes (capability 0x0001, privacy bit not set), then only the SSID "test"
        byte[] data = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0x64, 0x00,
                0x01, 0x00,
                0x00, 0x04, 0x74, 0x65, 0x73, 0x74
        };

        Beacon beacon = beaconParser.parse(data, 0, data.length);

        assertThat(classifier.classify(data, beacon)).isEqualTo("Open");
    }

    @Test
    void givenRsnWithPskAndSae_ThenNetworkIsWpa2Wpa3Transition(){

        BeaconParser beaconParser = new BeaconParser();
        SecurityClassifier classifier = new SecurityClassifier();

        // capability 0x0011 (privacy set), then RSN: version 1, group CCMP, 1 pairwise CCMP, 2 AKMs: PSK (2) and SAE (8)
        byte[] data = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0x64, 0x00,
                0x11, 0x00,
                0x30, 0x16,
                0x01, 0x00,
                0x00, 0x0f, (byte) 0xac, 0x04,
                0x01, 0x00, 0x00, 0x0f, (byte) 0xac, 0x04,
                0x02, 0x00, 0x00, 0x0f, (byte) 0xac, 0x02, 0x00, 0x0f, (byte) 0xac, 0x08
        };

        Beacon beacon = beaconParser.parse(data, 0, data.length);

        assertThat(classifier.classify(data, beacon)).isEqualTo("WPA2/WPA3");
    }
}
