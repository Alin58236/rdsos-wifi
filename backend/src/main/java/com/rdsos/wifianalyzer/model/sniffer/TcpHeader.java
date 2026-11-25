package com.rdsos.wifianalyzer.model.sniffer;

import lombok.Builder;
import lombok.Value;

/** TCP header, every field and the 9 flags (RFC 793, RFC 3168, RFC 3540). */
@Value
@Builder
public class TcpHeader {

    int sourcePort;
    int destinationPort;
    /** Unsigned 32-bit, so long. */
    long sequenceNumber;
    long acknowledgmentNumber;
    /** Data offset x 4: 20 without options. */
    int headerLengthBytes;

    // the 9 flags
    boolean nonceSum;
    boolean congestionWindowReduced;
    boolean ecnEcho;
    boolean urgent;
    boolean acknowledgment;
    boolean push;
    boolean reset;
    boolean synchronize;
    boolean finish;

    int receiveWindowSize;
    int checksum;
    int urgentPointer;
    /** Empty when the header is 20 bytes. */
    byte[] options;

}
