package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.sniffer.LlcSnapHeader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LlcHeaderParserTest {

    @Test
    void givenAnLlcSnapHeaderWithIpv4_ThenEveryFieldIsRead(){

        LlcHeaderParser parser = new LlcHeaderParser();

        // AA AA 03 = LLC with SNAP, 00 00 00 = "an EtherType follows", 08 00 = IPv4
        byte[] data = {(byte) 0xaa, (byte) 0xaa, 0x03, 0x00, 0x00, 0x00, 0x08, 0x00};

        LlcSnapHeader result = parser.parse(data, 0);

        LlcSnapHeader expected = LlcSnapHeader.builder()
                .destinationServiceAccessPoint(0xaa)
                .sourceServiceAccessPoint(0xaa)
                .control(0x03)
                .organizationCode("00:00:00")
                .etherType(0x0800)
                .build();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void givenTheHeaderAfterAQosDataHeader_ThenItIsReadAtTheOffset(){

        LlcHeaderParser parser = new LlcHeaderParser();

        // 26 bytes standing in for a QoS data header, then LLC/SNAP for IPv6 (86 dd)
        byte[] data = new byte[26 + 8];
        byte[] llcSnap = {(byte) 0xaa, (byte) 0xaa, 0x03, 0x00, 0x00, 0x00, (byte) 0x86, (byte) 0xdd};
        System.arraycopy(llcSnap, 0, data, 26, llcSnap.length);

        LlcSnapHeader result = parser.parse(data, 26);

        assertThat(result.getEtherType()).isEqualTo(0x86dd);
    }

    @Test
    void givenAnLlcHeaderThatIsNotSnap_ThenThrows(){

        LlcHeaderParser parser = new LlcHeaderParser();

        // 42 42 03 = LLC for Spanning Tree, which has no SNAP part and no EtherType
        byte[] data = {0x42, 0x42, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenLessThan8Bytes_ThenThrows(){

        LlcHeaderParser parser = new LlcHeaderParser();

        byte[] data = {(byte) 0xaa, (byte) 0xaa, 0x03, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> parser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
