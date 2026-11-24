package com.rdsos.wifianalyzer.util;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

@Component
public class OuiDatabase {

    private static final String HEX_MARKER = "(hex)";
    private static final String BUNDLED_FILE = "/oui.txt.gz";

    private final Map<String, String> vendors = new HashMap<>();

    public OuiDatabase() throws IOException {
        this(new GZIPInputStream(Objects.requireNonNull(
                OuiDatabase.class.getResourceAsStream(BUNDLED_FILE), BUNDLED_FILE + " is missing from the classpath")));
    }

    public OuiDatabase(InputStream testsInputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testsInputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int marker = line.indexOf(HEX_MARKER);
                if (marker < 8) {
                    continue;
                }
                String prefix = line.substring(0, 8).replace('-', ':').toLowerCase(Locale.ROOT);
                String vendor = line.substring(marker + HEX_MARKER.length()).trim();
                vendors.put(prefix, vendor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the OUI file", e);
        }
    }

    public String vendor(String bssid) {
        if (bssid == null || bssid.length() < 8) {
            return null;
        }
        // bit 0x02 of the first byte marks a locally administered (random) address, which has no vendor
        int firstByte = Integer.parseInt(bssid.substring(0, 2), 16);
        if ((firstByte & 0x02) != 0) {
            return "Local/Unknown";
        }
        return vendors.get(bssid.substring(0, 8).toLowerCase(Locale.ROOT));
    }
}
