package com.rdsos.wifianalyzer.model.sniffer;

import lombok.Builder;
import lombok.Value;

/** IPv4 header, every field (RFC 791). */
@Value
@Builder
public class Ipv4Header {

    int version;
    int headerLengthBytes;
    int differentiatedServicesCodePoint;
    int explicitCongestionNotification;
    int totalLengthBytes;
    int identification;
    boolean dontFragment;
    boolean moreFragments;
    int fragmentOffset;
    int timeToLive;
    /** The protocol that follows: 6 = TCP, 17 = UDP, 1 = ICMP. */
    int protocol;
    int headerChecksum;
    String sourceAddress;
    String destinationAddress;
    byte[] options;

}
