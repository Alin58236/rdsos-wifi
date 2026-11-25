package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs(OS.LINUX)
public class PcapLiveSourceTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");
    private static final Path WPA2_DATA = Path.of("src/test/resources/fixtures/wpa2-data.pcap");

    @Test
    void givenTheFirstBeacon_ThenLibpcapGivesTheSameFrameAsOurReader(){

        try (CaptureSource libpcap = PcapLiveSource.openFile(BEACONS_DATA);
             CaptureSource ourReader = new PcapFileReader(BEACONS_DATA)) {

            RawFrame fromLibpcap = libpcap.next();
            RawFrame fromOurReader = ourReader.next();

            assertThat(fromLibpcap.getLinkType()).isEqualTo(127);
            assertThat(fromLibpcap.getData()).hasSize(378);
            assertThat(fromLibpcap.getTimestampMicros()).isEqualTo(1795357761364086L);
            // RawFrame is a @Value, so isEqualTo compares every field, including the bytes
            assertThat(fromLibpcap).isEqualTo(fromOurReader);
        }
    }

    @Test
    void givenAWholeFile_ThenEveryFrameIsTheSameAndThenNull(){

        try (CaptureSource libpcap = PcapLiveSource.openFile(WPA2_DATA);
             CaptureSource ourReader = new PcapFileReader(WPA2_DATA)) {

            int count = 0;
            RawFrame fromOurReader = ourReader.next();
            while (fromOurReader != null) {
                assertThat(libpcap.next()).isEqualTo(fromOurReader);
                count++;
                fromOurReader = ourReader.next();
            }

            assertThat(count).isEqualTo(200);
            // pcap_next_ex returns -2 at the end of a file, which next() turns into null
            assertThat(libpcap.next()).isNull();
        }
    }

    @Test
    void givenAMissingFile_ThenLibpcapsErrorMessageIsThrown(){

        assertThatThrownBy(() -> PcapLiveSource.openFile(Path.of("src/test/resources/fixtures/missing.pcap")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.pcap");
    }
}
