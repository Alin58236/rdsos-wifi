package com.rdsos.wifianalyzer.model.sniffer;

import com.rdsos.wifianalyzer.model.analyzer.Beacon;
import com.rdsos.wifianalyzer.model.analyzer.Dot11Header;
import com.rdsos.wifianalyzer.model.analyzer.RadioTapHeader;
import lombok.Builder;
import lombok.Value;

/**
 * One frame decoded layer by layer, for the packet list. A layer that is not in the frame is null:
 * monitor mode has radioTap + dot11 (+ llcSnap when not encrypted), managed mode has ethernet instead.
 */
@Value
@Builder
public class DecodedPacket {

    long timestampMicros;
    byte[] data;

    RadioTapHeader radioTap;
    Dot11Header dot11;
    // only for beacons and probe responses, so the registry and the packet list share one parse
    Beacon beacon;
    boolean encrypted;
    LlcSnapHeader llcSnap;
    EthernetHeader ethernet;
    Ipv4Header ipv4;
    TcpHeader tcp;
    String httpPayload;

}
