package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.sniffer.Ipv4Header;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.util.Arrays;

/**
 * Reads an IPv4 header (RFC 791). All multi-byte fields are big-endian (network byte order).
 *
 *  0      1      2  3     4  5    6  7          8     9      10 11      12 .. 15   16 .. 19   20 ..
 *  ver+IHL DSCP+ECN total  ident  flags+offset  TTL   proto  checksum  source     destination options
 */
public class Ipv4HeaderParser {

    private static final int MINIMUM_HEADER_LENGTH = 20;

    // the flags share a 16-bit field with the fragment offset
    private static final int DONT_FRAGMENT_BIT = 0x4000;
    private static final int MORE_FRAGMENTS_BIT = 0x2000;
    private static final int FRAGMENT_OFFSET_MASK = 0x1FFF;

    public Ipv4Header parse(byte[] data, int offset){

        if(offset + MINIMUM_HEADER_LENGTH > data.length){
            throw new IllegalArgumentException("Truncated IPv4 header");
        }

        // byte 0: version in the high 4 bits, IHL (header length in 32-bit words) in the low 4 bits
        int firstByte = ByteReader.unsigned8(data, offset);
        int version = firstByte >> 4;
        if(version != 4){
            throw new IllegalArgumentException("Not IPv4: version " + version);
        }

        int headerLengthBytes = (firstByte & 0x0F) * 4;
        if(headerLengthBytes < MINIMUM_HEADER_LENGTH || offset + headerLengthBytes > data.length){
            throw new IllegalArgumentException("Invalid IPv4 header length: " + headerLengthBytes);
        }

        // byte 1: DSCP in the high 6 bits, ECN in the low 2 bits
        int secondByte = ByteReader.unsigned8(data, offset + 1);
        int dscp = secondByte >> 2;
        int ecn = secondByte & 0x03;

        int totalLengthBytes = ByteReader.unsigned16BigEndian(data, offset + 2);
        int identification = ByteReader.unsigned16BigEndian(data, offset + 4);

        int flagsAndFragmentOffset = ByteReader.unsigned16BigEndian(data, offset + 6);
        boolean dontFragment = (flagsAndFragmentOffset & DONT_FRAGMENT_BIT) != 0;
        boolean moreFragments = (flagsAndFragmentOffset & MORE_FRAGMENTS_BIT) != 0;
        int fragmentOffset = flagsAndFragmentOffset & FRAGMENT_OFFSET_MASK;

        int timeToLive = ByteReader.unsigned8(data, offset + 8);
        int protocol = ByteReader.unsigned8(data, offset + 9);
        int headerChecksum = ByteReader.unsigned16BigEndian(data, offset + 10);

        String sourceAddress = ByteReader.ipv4Address(data, offset + 12);
        String destinationAddress = ByteReader.ipv4Address(data, offset + 16);

        byte[] options = Arrays.copyOfRange(data, offset + MINIMUM_HEADER_LENGTH, offset + headerLengthBytes);

        return Ipv4Header.builder()
                .version(version)
                .headerLengthBytes(headerLengthBytes)
                .differentiatedServicesCodePoint(dscp)
                .explicitCongestionNotification(ecn)
                .totalLengthBytes(totalLengthBytes)
                .identification(identification)
                .dontFragment(dontFragment)
                .moreFragments(moreFragments)
                .fragmentOffset(fragmentOffset)
                .timeToLive(timeToLive)
                .protocol(protocol)
                .headerChecksum(headerChecksum)
                .sourceAddress(sourceAddress)
                .destinationAddress(destinationAddress)
                .options(options)
                .build();
    }

}
