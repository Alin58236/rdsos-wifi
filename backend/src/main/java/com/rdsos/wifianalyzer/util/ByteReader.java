package com.rdsos.wifianalyzer.util;

import java.util.Objects;

public final class ByteReader {

    private ByteReader() {
    }

    public static int unsigned8(byte[] inputByte, int offset) {
        Objects.checkFromIndexSize(offset, 1, inputByte.length);
        return inputByte[offset] & 0xff;
    }

    public static int signed8(byte[] inputByte, int offset) {
        Objects.checkFromIndexSize(offset, 1, inputByte.length);
        return inputByte[offset];
    }

    public static int unsigned16LittleEndian(byte[] inputBytes, int offset) {
        Objects.checkFromIndexSize(offset, 2, inputBytes.length);
        int low = inputBytes[offset] & 0xFF;
        int high = inputBytes[offset + 1] & 0xFF;
        return low | (high << 8);
    }

    public static int unsigned16BigEndian(byte[] inputBytes, int offset) {
        Objects.checkFromIndexSize(offset, 2, inputBytes.length);
        int high = inputBytes[offset] & 0xFF;
        int low = inputBytes[offset + 1] & 0xFF;
        return (high << 8) | low;
    }

    public static long unsigned32LittleEndian(byte[] inputBytes, int offset) {
        Objects.checkFromIndexSize(offset, 4, inputBytes.length);
        return (inputBytes[offset] & 0xFFL)
                | (inputBytes[offset + 1] & 0xFFL) << 8
                | (inputBytes[offset + 2] & 0xFFL) << 16
                | (inputBytes[offset + 3] & 0xFFL) << 24;
    }

    public static long unsigned32BigEndian(byte[] inputBytes, int offset) {
        Objects.checkFromIndexSize(offset, 4, inputBytes.length);
        return (inputBytes[offset] & 0xFFL) << 24
                | (inputBytes[offset + 1] & 0xFFL) << 16
                | (inputBytes[offset + 2] & 0xFFL) << 8
                | (inputBytes[offset + 3] & 0xFFL);
    }

    public static String ipv4Address(byte[] input, int offset) {
        Objects.checkFromIndexSize(offset, 4, input.length);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            // & 0xFF: a Java byte is signed, so 0xc0 would otherwise be -64 instead of 192
            result.append(input[offset + i] & 0xFF);
            if (i != 3) {
                result.append(".");
            }
        }
        return result.toString();
    }

    public static String mac(byte[] input, int offset) {
        Objects.checkFromIndexSize(offset, 6, input.length);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            result.append(String.format("%02x", input[offset + i] & 0xFF));
            if (i != 5) {
                result.append(":");
            }
        }
        return result.toString();
    }
}
