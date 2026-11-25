package com.rdsos.wifianalyzer.service.sniffer;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.analyzer.Dot11Header;
import com.rdsos.wifianalyzer.model.analyzer.RadioTapHeader;
import com.rdsos.wifianalyzer.model.sniffer.*;
import com.rdsos.wifianalyzer.service.parser.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PacketDecoder {

    private final EthernetHeaderParser ethernetHeaderParser = new EthernetHeaderParser();
    private final RadioTapParser radioTapParser = new RadioTapParser();
    private final Dot11Parser dot11Parser  = new Dot11Parser();
    private final LlcHeaderParser llcHeaderParser = new LlcHeaderParser();
    private final Ipv4HeaderParser ipv4HeaderParser = new Ipv4HeaderParser();
    private final TcpHeaderParser tcpHeaderParser = new TcpHeaderParser();
    private final BeaconParser beaconParser = new BeaconParser();
    private final HttpPayloadParser httpPayloadParser = new HttpPayloadParser();

    public DecodedPacket decode(RawFrame frame){

        byte[] data = frame.getData();

        DecodedPacket.DecodedPacketBuilder packet = DecodedPacket.builder()
                .timestampMicros(frame.getTimestampMicros())
                .data(data);

        int offset;
        int etherType;

        if(frame.getLinkType() == 1){

            EthernetHeader ethernetHeader = ethernetHeaderParser.parse(data,0);
            packet.ethernet(ethernetHeader);
            etherType = ethernetHeader.getEtherType();
            offset = ethernetHeader.getHeaderLengthBytes();

        } else if(frame.getLinkType() == 127){

            RadioTapHeader radioTapHeader = radioTapParser.parse(data,0);
            packet.radioTap(radioTapHeader);

            Dot11Header dot11Header = dot11Parser.parse(data, radioTapHeader.getLength());
            packet.dot11(dot11Header);

            if(isBeaconOrProbeResponse(dot11Header)){
                int bodyOffset = radioTapHeader.getLength() + dot11Header.getHeaderLength();
                int end = radioTapHeader.isHasFrameCheckSequence() ? data.length - 4 : data.length;
                packet.beacon(beaconParser.parse(data, bodyOffset, end));
            }

            if(dot11Header.getType() != 2){
                return packet.build();
            }

            if(dot11Header.isProtectedFrame()){
                packet.encrypted(true);
                return packet.build();
            }

            if(hasNoData(dot11Header.getSubtype())){
                return packet.build();
            }

            int llcOffset = radioTapHeader.getLength() + dot11Header.getHeaderLength();
            LlcSnapHeader llcSnapHeader = llcHeaderParser.parse(data, llcOffset);
            packet.llcSnap(llcSnapHeader);

            etherType = llcSnapHeader.getEtherType();
            offset = llcOffset + 8;

        } else {
            throw new IllegalArgumentException("Unsupported link type: " + frame.getLinkType());
        }


        if(etherType != 0x0800){
            return packet.build();
        }

        Ipv4Header ipv4Header = ipv4HeaderParser.parse(data,offset);

        packet.ipv4(ipv4Header);
        offset += ipv4Header.getHeaderLengthBytes();

        if(ipv4Header.getProtocol() != 6){
            log.debug("Packet doesn't have TCP");
            return packet.build();
        }

        TcpHeader tcpHeader = tcpHeaderParser.parse(data,offset);
        packet.tcp(tcpHeader);
        offset += tcpHeader.getHeaderLengthBytes();


        int payloadLength = ipv4Header.getTotalLengthBytes() - ipv4Header.getHeaderLengthBytes() - tcpHeader.getHeaderLengthBytes();
        boolean isHttpPort = tcpHeader.getSourcePort() == 80 || tcpHeader.getDestinationPort() ==80;

        if(isHttpPort && payloadLength>0){
            String payload = httpPayloadParser.parse(data,offset,payloadLength);
            packet.httpPayload(payload);
        }

        return packet.build();

    }

    // management (0), subtype 8 = beacon, 5 = probe response: both carry SSID, channel and security
    private boolean isBeaconOrProbeResponse(Dot11Header dot11Header){
        return dot11Header.getType() == 0 && (dot11Header.getSubtype() == 8 || dot11Header.getSubtype() == 5);
    }

    private boolean hasNoData(int subtype){
        return (subtype & 0x04) != 0;
    }

}
