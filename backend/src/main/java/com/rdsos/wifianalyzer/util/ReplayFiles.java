package com.rdsos.wifianalyzer.util;

import com.rdsos.wifianalyzer.config.WifiProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The .pcap files in wifi.replay-dir that the UI may replay.
 * A request names a file; it never sends a path, because the backend runs as root.
 */
@Component
public class ReplayFiles {

    private final Path replayDir;

    public ReplayFiles(WifiProperties properties) {
        this.replayDir = properties.getReplayDir();
    }

    public List<String> list() {
        if (!Files.isDirectory(replayDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(replayDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".pcap"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + replayDir, e);
        }
    }

    public Path resolve(String name) {
        if (name == null || !list().contains(name)) {
            throw new IllegalArgumentException("Unknown replay file: " + name);
        }
        return replayDir.resolve(name);
    }
}
