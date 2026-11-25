package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.sniffer.TcpHeader;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.util.Arrays;

public class TcpHeaderParser {

    private static final int MINIMUM_HEADER_LENGTH = 20;

    // byte 12 mask
    private static final int NONCE_SUM_BIT = 0x01;

    // byte 13 masks
    private static final int CONGESTION_WINDOW_REDUCED_BIT = 0x80;
    private static final int ECN_ECHO_BIT = 0x40;
    private static final int URGENT_BIT = 0x20;
    private static final int ACKNOWLEDGMENT_BIT = 0x10;
    private static final int PUSH_BIT = 0x08;
    private static final int RESET_BIT = 0x04;
    private static final int SYNCHRONIZE_BIT = 0x02;
    private static final int FINISH_BIT = 0x01;

    public TcpHeader parse(byte[] data, int offset){

        if(offset + MINIMUM_HEADER_LENGTH > data.length){
            throw new IllegalArgumentException("Truncated TCP Header");
        }

        int sourcePort = ByteReader.unsigned16BigEndian(data, offset);
        int destinationPort = ByteReader.unsigned16BigEndian(data, offset + 2);
        long sequenceNumber = ByteReader.unsigned32BigEndian(data, offset + 4);
        long ackNumber = ByteReader.unsigned32BigEndian(data, offset + 8);

        int byte12 = ByteReader.unsigned8(data, offset + 12);
        int headerLengthBytes = (byte12 >> 4) * 4;
        boolean nonceSum = (byte12 & NONCE_SUM_BIT) != 0;

        if(headerLengthBytes < MINIMUM_HEADER_LENGTH || offset + headerLengthBytes > data.length){
            throw new IllegalArgumentException("Invalid TCP header length: " + headerLengthBytes);
        }

        int byte13 = ByteReader.unsigned8(data, offset + 13);
        boolean congestionWindowReduced = (byte13 & CONGESTION_WINDOW_REDUCED_BIT) != 0;
        boolean ecnEcho = (byte13 & ECN_ECHO_BIT) != 0;
        boolean urgent = (byte13 & URGENT_BIT) != 0;
        boolean acknowledgment = (byte13 & ACKNOWLEDGMENT_BIT) != 0;
        boolean push = (byte13 & PUSH_BIT) != 0;
        boolean reset = (byte13 & RESET_BIT) != 0;
        boolean synchronize = (byte13 & SYNCHRONIZE_BIT) != 0;
        boolean finish = (byte13 & FINISH_BIT) != 0;

        int receiveWindowSize = ByteReader.unsigned16BigEndian(data, offset + 14);
        int checksum = ByteReader.unsigned16BigEndian(data, offset + 16);
        int urgentPointer = ByteReader.unsigned16BigEndian(data, offset + 18);

        byte[] options = Arrays.copyOfRange(data, offset + MINIMUM_HEADER_LENGTH, offset + headerLengthBytes);

        return TcpHeader.builder()
                .sourcePort(sourcePort)
                .destinationPort(destinationPort)
                .sequenceNumber(sequenceNumber)
                .acknowledgmentNumber(ackNumber)
                .headerLengthBytes(headerLengthBytes)
                .nonceSum(nonceSum)
                .congestionWindowReduced(congestionWindowReduced)
                .ecnEcho(ecnEcho)
                .urgent(urgent)
                .acknowledgment(acknowledgment)
                .push(push)
                .reset(reset)
                .synchronize(synchronize)
                .finish(finish)
                .receiveWindowSize(receiveWindowSize)
                .checksum(checksum)
                .urgentPointer(urgentPointer)
                .options(options)
                .build();
    }

}
