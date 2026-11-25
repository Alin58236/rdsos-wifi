package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.PacketDetail;
import com.rdsos.wifianalyzer.api.dto.PacketField;
import com.rdsos.wifianalyzer.api.dto.PacketLayer;
import com.rdsos.wifianalyzer.api.dto.PacketSummary;
import com.rdsos.wifianalyzer.model.analyzer.Beacon;
import com.rdsos.wifianalyzer.model.analyzer.Dot11Header;
import com.rdsos.wifianalyzer.model.analyzer.RadioTapHeader;
import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.model.sniffer.EthernetHeader;
import com.rdsos.wifianalyzer.model.sniffer.Ipv4Header;
import com.rdsos.wifianalyzer.model.sniffer.LlcSnapHeader;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import com.rdsos.wifianalyzer.model.sniffer.TcpHeader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
public class PacketViewMapper {

    private static final int LLC_SNAP_LENGTH = 8;
    private static final int FCS_LENGTH = 4;

    public PacketSummary toSummary(StoredPacket stored){
        DecodedPacket packet = stored.getPacket();

        return new PacketSummary(
                stored.getId(),
                packet.getTimestampMicros(),
                packet.getData().length,
                protocol(packet),
                source(packet),
                destination(packet),
                info(packet));
    }

    private String protocol(DecodedPacket packet){
        if(packet.getHttpPayload() != null) return "HTTP";
        if(packet.getTcp() != null) return "TCP";
        if(packet.getIpv4() != null) return "IPv4";
        if(packet.isEncrypted()) return "Encrypted (WPA2)";
        if(packet.getEthernet() != null) return "Ethernet";
        if(packet.getLlcSnap() != null) return "LLC/SNAP";
        if(packet.getDot11() != null) return "802.11 " + subtypeName(packet.getDot11());
        return "Unknown";
    }

    // IP addresses when there is an IP layer, otherwise MAC addresses
    private String source(DecodedPacket packet){
        if(packet.getIpv4() != null) return packet.getIpv4().getSourceAddress();
        if(packet.getEthernet() != null) return packet.getEthernet().getSourceMac();
        if(packet.getDot11() != null) return packet.getDot11().getSource();
        return null;
    }

    private String destination(DecodedPacket packet){
        if(packet.getIpv4() != null) return packet.getIpv4().getDestinationAddress();
        if(packet.getEthernet() != null) return packet.getEthernet().getDestinationMac();
        if(packet.getDot11() != null) return packet.getDot11().getDestination();
        return null;
    }

    private String info(DecodedPacket packet){

        if(packet.getHttpPayload() != null){
            return firstLine(packet.getHttpPayload());
        }

        TcpHeader tcp = packet.getTcp();
        if(tcp != null){
            // e.g. "39194 → 80 [SYN]"
            return tcp.getSourcePort() + " → " + tcp.getDestinationPort() + " [" + tcpFlags(tcp) + "]";
        }

        if(packet.getIpv4() != null){
            return "Protocol " + ipProtocolText(packet.getIpv4().getProtocol());
        }

        Beacon beacon = packet.getBeacon();
        if(beacon != null){
            String ssid = beacon.getSsid().isEmpty() ? "<hidden>" : beacon.getSsid();
            String channel = beacon.getChannel() == null ? "?" : beacon.getChannel().toString();
            return "SSID=" + ssid + " CH=" + channel;
        }

        if(packet.isEncrypted()){
            return "Protected data";
        }

        if(packet.getEthernet() != null){
            return "EtherType " + hex16(packet.getEthernet().getEtherType());
        }

        if(packet.getLlcSnap() != null){
            return "EtherType " + hex16(packet.getLlcSnap().getEtherType());
        }

        return "";
    }

    private String firstLine(String text){
        int lineEnd = text.indexOf("\r\n");
        return lineEnd == -1 ? text : text.substring(0, lineEnd);
    }

    public PacketDetail toDetail(StoredPacket stored){

        DecodedPacket packet = stored.getPacket();
        byte[] data = packet.getData();
        List<PacketLayer> layers = new ArrayList<>();

        int offset = 0;

        RadioTapHeader radioTap = packet.getRadioTap();
        if(radioTap != null){
            layers.add(radioTapLayer(radioTap));
            offset += radioTap.getLength();
        }

        Dot11Header dot11 = packet.getDot11();
        if(dot11 != null){
            layers.add(dot11Layer(dot11, offset));
            offset += dot11.getHeaderLength();

            // the body runs to the end of the frame, without the 4-byte FCS when the card added one
            int bodyEnd = (radioTap != null && radioTap.isHasFrameCheckSequence()) ? data.length - FCS_LENGTH : data.length;

            if(packet.getBeacon() != null){
                layers.add(beaconLayer(packet.getBeacon(), offset, bodyEnd - offset));
            }
            if(packet.isEncrypted()){
                // WPA2 ciphertext: we know where it is, but not what is inside
                layers.add(new PacketLayer("Encrypted (WPA2)", offset, bodyEnd - offset, List.of()));
            }
        }

        LlcSnapHeader llcSnap = packet.getLlcSnap();
        if(llcSnap != null){
            layers.add(llcSnapLayer(llcSnap, offset));
            offset += LLC_SNAP_LENGTH;
        }

        EthernetHeader ethernet = packet.getEthernet();
        if(ethernet != null){
            layers.add(ethernetLayer(ethernet));
            offset += ethernet.getHeaderLengthBytes();
        }

        Ipv4Header ipv4 = packet.getIpv4();
        if(ipv4 != null){
            layers.add(ipv4Layer(ipv4, offset));
            offset += ipv4.getHeaderLengthBytes();
        }

        TcpHeader tcp = packet.getTcp();
        if(tcp != null){
            layers.add(tcpLayer(tcp, offset));
            offset += tcp.getHeaderLengthBytes();
        }

        String httpPayload = packet.getHttpPayload();
        if(httpPayload != null){
            // HttpPayloadParser gives one character per byte, so the text length is the byte length
            layers.add(new PacketLayer("HTTP", offset, httpPayload.length(),
                    List.of(new PacketField("Payload", httpPayload))));
        }

        return new PacketDetail(stored.getId(), packet.getTimestampMicros(), data.length, layers, toHex(data));
    }

    private PacketLayer radioTapLayer(RadioTapHeader radioTap){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("Header length", radioTap.getLength() + " bytes"));
        fields.add(new PacketField("Frequency", radioTap.getFreqMHz() == null ? "unknown" : radioTap.getFreqMHz() + " MHz"));
        fields.add(new PacketField("Signal", radioTap.getSignalDBm() == null ? "unknown" : radioTap.getSignalDBm() + " dBm"));
        fields.add(new PacketField("FCS at end", yesNo(radioTap.isHasFrameCheckSequence())));

        return new PacketLayer("Radiotap", 0, radioTap.getLength(), fields);
    }

    private PacketLayer dot11Layer(Dot11Header dot11, int offset){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("Type / subtype",
                typeName(dot11.getType()) + " (" + dot11.getType() + ") / " + subtypeName(dot11) + " (" + dot11.getSubtype() + ")"));
        fields.add(new PacketField("To DS", yesNo(dot11.isToDs())));
        fields.add(new PacketField("From DS", yesNo(dot11.isFromDs())));
        fields.add(new PacketField("Protected", yesNo(dot11.isProtectedFrame())));

        // ACK and CTS have only a destination, so only the addresses that exist are shown
        if(dot11.getDestination() != null) fields.add(new PacketField("Destination", dot11.getDestination()));
        if(dot11.getSource() != null) fields.add(new PacketField("Source", dot11.getSource()));
        if(dot11.getBssid() != null) fields.add(new PacketField("BSSID", dot11.getBssid()));

        return new PacketLayer("802.11 " + subtypeName(dot11), offset, dot11.getHeaderLength(), fields);
    }

    private PacketLayer beaconLayer(Beacon beacon, int offset, int length){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("SSID", beacon.getSsid().isEmpty() ? "<hidden>" : beacon.getSsid()));
        fields.add(new PacketField("Channel", beacon.getChannel() == null ? "unknown" : beacon.getChannel().toString()));
        fields.add(new PacketField("Beacon interval", beacon.getBeaconInterval() + " TU"));
        fields.add(new PacketField("Privacy", yesNo(beacon.isPrivacy())));

        return new PacketLayer("Beacon body", offset, length, fields);
    }

    private PacketLayer llcSnapLayer(LlcSnapHeader llcSnap, int offset){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("DSAP", hex8(llcSnap.getDestinationServiceAccessPoint())));
        fields.add(new PacketField("SSAP", hex8(llcSnap.getSourceServiceAccessPoint())));
        fields.add(new PacketField("Control", hex8(llcSnap.getControl())));
        fields.add(new PacketField("Organization code", llcSnap.getOrganizationCode()));
        fields.add(new PacketField("EtherType", etherTypeText(llcSnap.getEtherType())));

        return new PacketLayer("LLC/SNAP", offset, LLC_SNAP_LENGTH, fields);
    }

    private PacketLayer ethernetLayer(EthernetHeader ethernet){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("Destination", ethernet.getDestinationMac()));
        fields.add(new PacketField("Source", ethernet.getSourceMac()));
        fields.add(new PacketField("EtherType", etherTypeText(ethernet.getEtherType())));

        // the kernel removed the 802.11 header in managed mode, so this is not what went over the air
        return new PacketLayer("Ethernet (managed mode)", 0, ethernet.getHeaderLengthBytes(), fields);
    }

    // every IPv4 header field, in the order they are on the wire
    private PacketLayer ipv4Layer(Ipv4Header ipv4, int offset){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("Version", String.valueOf(ipv4.getVersion())));
        fields.add(new PacketField("Header length", ipv4.getHeaderLengthBytes() + " bytes"));
        fields.add(new PacketField("DSCP", String.valueOf(ipv4.getDifferentiatedServicesCodePoint())));
        fields.add(new PacketField("ECN", String.valueOf(ipv4.getExplicitCongestionNotification())));
        fields.add(new PacketField("Total length", String.valueOf(ipv4.getTotalLengthBytes())));
        fields.add(new PacketField("Identification", hex16(ipv4.getIdentification()) + " (" + ipv4.getIdentification() + ")"));
        fields.add(new PacketField("Don't fragment", yesNo(ipv4.isDontFragment())));
        fields.add(new PacketField("More fragments", yesNo(ipv4.isMoreFragments())));
        fields.add(new PacketField("Fragment offset", String.valueOf(ipv4.getFragmentOffset())));
        fields.add(new PacketField("Time to live", String.valueOf(ipv4.getTimeToLive())));
        fields.add(new PacketField("Protocol", ipProtocolText(ipv4.getProtocol())));
        fields.add(new PacketField("Header checksum", hex16(ipv4.getHeaderChecksum())));
        fields.add(new PacketField("Source", ipv4.getSourceAddress()));
        fields.add(new PacketField("Destination", ipv4.getDestinationAddress()));
        fields.add(new PacketField("Options", optionsText(ipv4.getOptions())));

        return new PacketLayer("IPv4", offset, ipv4.getHeaderLengthBytes(), fields);
    }

    // every TCP header field, in the order they are on the wire
    private PacketLayer tcpLayer(TcpHeader tcp, int offset){

        List<PacketField> fields = new ArrayList<>();
        fields.add(new PacketField("Source port", String.valueOf(tcp.getSourcePort())));
        fields.add(new PacketField("Destination port", String.valueOf(tcp.getDestinationPort())));
        fields.add(new PacketField("Sequence number", String.valueOf(tcp.getSequenceNumber())));
        fields.add(new PacketField("Acknowledgment number", String.valueOf(tcp.getAcknowledgmentNumber())));
        fields.add(new PacketField("Header length", tcp.getHeaderLengthBytes() + " bytes"));
        fields.add(new PacketField("Flags", tcpFlags(tcp)));
        fields.add(new PacketField("Window size", String.valueOf(tcp.getReceiveWindowSize())));
        fields.add(new PacketField("Checksum", hex16(tcp.getChecksum())));
        fields.add(new PacketField("Urgent pointer", String.valueOf(tcp.getUrgentPointer())));
        fields.add(new PacketField("Options", optionsText(tcp.getOptions())));

        return new PacketLayer("TCP", offset, tcp.getHeaderLengthBytes(), fields);
    }

    // ======================= names and number formats =======================

    // the set flags, in the order Wireshark prints them: "SYN, ACK", "PSH, ACK", "FIN, ACK"
    private String tcpFlags(TcpHeader tcp){

        List<String> flags = new ArrayList<>();
        if(tcp.isFinish()) flags.add("FIN");
        if(tcp.isSynchronize()) flags.add("SYN");
        if(tcp.isReset()) flags.add("RST");
        if(tcp.isPush()) flags.add("PSH");
        if(tcp.isAcknowledgment()) flags.add("ACK");
        if(tcp.isUrgent()) flags.add("URG");
        if(tcp.isEcnEcho()) flags.add("ECE");
        if(tcp.isCongestionWindowReduced()) flags.add("CWR");
        if(tcp.isNonceSum()) flags.add("NS");

        return flags.isEmpty() ? "none" : String.join(", ", flags);
    }

    private String typeName(int type){
        switch(type){
            case 0: return "Management";
            case 1: return "Control";
            case 2: return "Data";
            default: return "Extension";
        }
    }

    private String subtypeName(Dot11Header dot11){

        int subtype = dot11.getSubtype();

        if(dot11.getType() == 0){
            switch(subtype){
                case 0: return "Association Request";
                case 1: return "Association Response";
                case 4: return "Probe Request";
                case 5: return "Probe Response";
                case 8: return "Beacon";
                case 10: return "Disassociation";
                case 11: return "Authentication";
                case 12: return "Deauthentication";
                case 13: return "Action";
            }
        }

        if(dot11.getType() == 1){
            switch(subtype){
                case 8: return "Block Ack Request";
                case 9: return "Block Ack";
                case 11: return "RTS";
                case 12: return "CTS";
                case 13: return "ACK";
            }
        }

        if(dot11.getType() == 2){
            switch(subtype){
                case 0: return "Data";
                case 4: return "Null";
                case 8: return "QoS Data";
                case 12: return "QoS Null";
            }
        }

        return "Subtype " + subtype;
    }

    private String etherTypeText(int etherType){
        switch(etherType){
            case 0x0800: return hex16(etherType) + " (IPv4)";
            case 0x0806: return hex16(etherType) + " (ARP)";
            case 0x86dd: return hex16(etherType) + " (IPv6)";
            case 0x888e: return hex16(etherType) + " (EAPOL)";
            default: return hex16(etherType);
        }
    }

    private String ipProtocolText(int protocol){
        switch(protocol){
            case 1: return "1 (ICMP)";
            case 6: return "6 (TCP)";
            case 17: return "17 (UDP)";
            default: return String.valueOf(protocol);
        }
    }

    // options are not decoded (dropped from the scope), so they are shown as raw hex
    private String optionsText(byte[] options){
        return options.length == 0 ? "none" : toHex(options);
    }

    private String yesNo(boolean value){
        return value ? "yes" : "no";
    }

    private String hex8(int value){
        return String.format("0x%02x", value);
    }

    private String hex16(int value){
        return String.format("0x%04x", value);
    }

    private String toHex(byte[] bytes){
        StringBuilder result = new StringBuilder();
        for(byte b : bytes){
            result.append(String.format("%02x", b & 0xFF));
        }
        return result.toString();
    }
}
