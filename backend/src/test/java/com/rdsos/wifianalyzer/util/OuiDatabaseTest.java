package com.rdsos.wifianalyzer.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OuiDatabaseTest {

    // two lines in the same format as oui.txt.gz
    private static final String SMALL_FILE =
            "50-D4-F7   (hex)\t\tTP-LINK TECHNOLOGIES CO.,LTD.\n" +
            "00-1A-11   (hex)\t\tGoogle, Inc.\n";

    private OuiDatabase smallDatabase() {
        InputStream in = new ByteArrayInputStream(SMALL_FILE.getBytes(StandardCharsets.UTF_8));
        return new OuiDatabase(in);
    }

    @Test
    void givenKnownPrefix_ThenReturnTheVendor(){

        OuiDatabase database = smallDatabase();

        assertThat(database.vendor("50:d4:f7:03:12:f3")).isEqualTo("TP-LINK TECHNOLOGIES CO.,LTD.");
        assertThat(database.vendor("00:1a:11:aa:bb:cc")).isEqualTo("Google, Inc.");
    }

    @Test
    void givenRandomMac_ThenReturnLocallyAdministered(){

        OuiDatabase database = smallDatabase();

        // ae = 1010 1110, bit 0x02 is set, so the MAC is random (the phone in wpa2-data.pcap)
        assertThat(database.vendor("ae:80:66:e8:2f:e3")).isEqualTo("Local/Unknown");
    }

    @Test
    void givenUnknownPrefix_ThenReturnNull(){

        OuiDatabase database = smallDatabase();

        assertThat(database.vendor("00:11:22:33:44:55")).isNull();
    }

    @Test
    void givenTheRealFile_ThenTheRouterIsTpLink() throws IOException {

        try (InputStream in = new GZIPInputStream(OuiDatabase.class.getResourceAsStream("/oui.txt.gz"))) {

            OuiDatabase database = new OuiDatabase(in);

            assertThat(database.vendor("50:d4:f7:03:12:f3")).isEqualTo("TP-LINK TECHNOLOGIES CO.,LTD.");
        }
    }

    @Test
    void givenTheConstructorSpringUses_ThenTheBundledFileIsLoaded() throws IOException {

        OuiDatabase database = new OuiDatabase();

        assertThat(database.vendor("50:d4:f7:03:12:f3")).isEqualTo("TP-LINK TECHNOLOGIES CO.,LTD.");
    }
}
