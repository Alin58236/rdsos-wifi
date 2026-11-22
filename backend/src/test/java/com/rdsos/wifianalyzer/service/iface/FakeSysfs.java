package com.rdsos.wifianalyzer.iface;

import com.rdsos.wifianalyzer.config.WifiProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds a fake /sys/class/net tree in a temp directory. */
final class FakeSysfs {

    private FakeSysfs() {
    }

    static void addInterface(Path root, String name, boolean wireless, int type) {
        try {
            Path dir = Files.createDirectories(root.resolve(name));
            if (wireless) {
                Files.createDirectories(dir.resolve("phy80211"));
            }
            Files.writeString(dir.resolve("type"), type + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Default properties (channels 1-13, 250 ms) pointing at the fake sysfs root. */
    static WifiProperties properties(Path root, String driverModule) {
        WifiProperties properties = new WifiProperties();
        properties.setDriverModule(driverModule);
        properties.setSysClassNet(root);
        return properties;
    }
}
