package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.config.WifiProperties;
import com.rdsos.wifianalyzer.util.ReplayFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayFilesTest {

    @TempDir
    Path folder;

    private ReplayFiles replayFilesIn(Path directory) {
        WifiProperties properties = new WifiProperties();
        properties.setReplayDir(directory);
        return new ReplayFiles(properties);
    }

    @Test
    void listsOnlyPcapFilesSortedByName() throws IOException {
        Files.writeString(folder.resolve("wpa2-data.pcap"), "");
        Files.writeString(folder.resolve("beacons-wpa2.pcap"), "");
        Files.writeString(folder.resolve("notes.txt"), "");

        assertThat(replayFilesIn(folder).list()).containsExactly("beacons-wpa2.pcap", "wpa2-data.pcap");
    }

    @Test
    void listsNothingWhenTheFolderIsMissing() {
        assertThat(replayFilesIn(folder.resolve("missing")).list()).isEmpty();
    }

    @Test
    void resolvesAListedFile() throws IOException {
        Files.writeString(folder.resolve("beacons-wpa2.pcap"), "");

        assertThat(replayFilesIn(folder).resolve("beacons-wpa2.pcap")).isEqualTo(folder.resolve("beacons-wpa2.pcap"));
    }

    @Test
    void refusesAPathInsteadOfAName() throws IOException {
        Files.writeString(folder.resolve("beacons-wpa2.pcap"), "");

        // the backend runs as root: a free path would let a request read any file on disk
        assertThatThrownBy(() -> replayFilesIn(folder).resolve("../../etc/shadow"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> replayFilesIn(folder).resolve("/etc/shadow"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANameThatIsNotInTheList() {
        assertThatThrownBy(() -> replayFilesIn(folder).resolve("other.pcap"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
