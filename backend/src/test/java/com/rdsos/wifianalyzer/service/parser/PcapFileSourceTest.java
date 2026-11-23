package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PcapFileSourceTest {

    @TempDir
    Path tmp;

    private static final Path BEACONS = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    private static final Path WPA_DATA = Path.of("src/test/resources/fixtures/wpa2-data.pcap");

    @Test
    void firstFrameHasTheValuesFromTheFile() {
        try (CaptureSource source = new PcapFileReader(BEACONS)) {
            RawFrame first = source.next();

            assertThat(first.getLinkType()).isEqualTo(127);
            assertThat(first.getData()).hasSize(378);
            assertThat(first.getOriginalLength()).isEqualTo(378);
            assertThat(first.getTimestampMicros()).isEqualTo(1795357761364086L);
            assertThat(first.getData()[2]).isEqualTo((byte) 0x26);
        }
    }


    @Test
    public void readsAllFiftyBeaconsThenReturnsNull(){

        int counter = 0;
        try (CaptureSource source = new PcapFileReader(BEACONS)) {
            while(source.next()!=null){
                counter++;
            }

            assertThat(source.next()).isNull();
        }

        assertThat(counter).isEqualTo(50);

    }


    @Test
    public void readsAllFramesOfDifferentSizes(){

        int counter = 0;
        try (CaptureSource source = new PcapFileReader(WPA_DATA)) {
            while(source.next()!=null){
                counter++;
            }

            assertThat(source.next()).isNull();
        }

        assertThat(counter).isEqualTo(200);

    }

    @Test
    void rejectsAFileShorterThanTheGlobalHeader() throws IOException {
        Path file = tmp.resolve("short.pcap");
        Files.write(file, new byte[10]);

        assertThatThrownBy(() -> new PcapFileReader(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownMagic() throws IOException {
        Path file = tmp.resolve("unknown.pcap");
        Files.write(file, new byte[24]);

        assertThatThrownBy(() -> new PcapFileReader(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPcapngFile() throws IOException {
        byte[] content = new byte[24];
        content[0] = 0x0A;
        content[1] = 0x0D;
        content[2] = 0x0D;
        content[3] = 0x0A;
        Path file = tmp.resolve("capture.pcapng");
        Files.write(file, content);

        assertThatThrownBy(() -> new PcapFileReader(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsATruncatedRecordHeader() throws IOException {
        byte[] globalHeader = Arrays.copyOf(Files.readAllBytes(BEACONS), 24);
        Path file = tmp.resolve("truncated.pcap");
        Files.write(file, Arrays.copyOf(globalHeader, 24 + 5));

        try (CaptureSource source = new PcapFileReader(file)) {
            assertThatThrownBy(source::next)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

}
