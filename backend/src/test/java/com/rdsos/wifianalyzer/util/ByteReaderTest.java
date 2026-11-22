package com.rdsos.wifianalyzer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteReaderTest {

    @Test
    public void givenAnUnsigned8TypeByte_thenReturnValidU8() {
        byte[] inputByte = {(byte) 0xFF};
        int outputByte = 255;

        assertEquals(outputByte, ByteReader.unsigned8(inputByte, 0));
    }

    @Test
    public void givenAnSigned8TypeByte_thenReturnValidS8() {
        byte[] inputByte = {(byte) 0xA7};
        int outputByte = -89;

        assertEquals(outputByte, ByteReader.signed8(inputByte, 0));
    }

    @Test
    public void givenUnsigned16LittleEndian_thenReturnValidIntValue() {
        byte[] inputBytes = {0x26, 0x00};
        int outputByte = 38;

        assertEquals(outputByte, ByteReader.unsigned16LittleEndian(inputBytes, 0));
    }

    @Test
    public void givenUnsigned16BigEndian_thenReturnValidIntValue() {
        byte[] inputBytes = {0x08, 0x00};
        int outputByte = 2048;

        assertEquals(outputByte, ByteReader.unsigned16BigEndian(inputBytes, 0));
    }

    @Test
    public void givenUnsigned32LittleEndian_thenReturnValidIntValue() {
        byte[] inputBytes = {(byte) 0xD4, (byte) 0xC3, (byte) 0xB2, (byte) 0xA1};
        long outputByte = 2712847316L;

        assertEquals(outputByte, ByteReader.unsigned32LittleEndian(inputBytes, 0));
    }

    @Test
    public void givenUnsigned32BigEndian_thenReturnValidIntValue() {
        byte[] inputBytes = {(byte) 0xD4, (byte) 0xC3, (byte) 0xB2, (byte) 0xA1};
        long outputByte = 3569595041L;

        assertEquals(outputByte, ByteReader.unsigned32BigEndian(inputBytes, 0));
    }

    @Test
    public void givenNonZeroOffset_thenReadsFromThatPosition() {
        byte[] inputBytes = {0x00, 0x00, 0x26, 0x00};
        int output = 38;

        assertEquals(output, ByteReader.unsigned16LittleEndian(inputBytes, 2));
    }

    @Test
    public void givenMACAddressBytes_ThenTranslateToActualMACAddress() {
        byte[] input = {0x50, (byte) 0xD4, (byte) 0xF7, 0x03, 0x12, (byte) 0xF3};
        String output = "50:d4:f7:03:12:f3";

        assertEquals(output, ByteReader.mac(input, 0));
    }

    @Test
    public void givenAShortVector_WhenReadingOverTheLastPosition_ThenThrowIndexOutOfBounds() {
        byte[] inputArray = {(byte) 0xFF};

        assertThatThrownBy(() -> ByteReader.unsigned16LittleEndian(inputArray, 0))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    public void givenANegativeOffset_ThenThrowIndexOutOfBounds() {
        byte[] inputArray = {(byte) 0xFF};

        assertThatThrownBy(() -> ByteReader.unsigned8(inputArray, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
