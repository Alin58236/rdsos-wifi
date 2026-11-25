package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.sniffer.LlcSnapHeader;
import com.rdsos.wifianalyzer.util.ByteReader;

public class LlcHeaderParser {

    public LlcSnapHeader parse(byte[] data, int offset){
        if(offset+8 > data.length){
            throw new IllegalArgumentException("LLC SNAP Header is truncated");
        }

        int dsap = ByteReader.unsigned8(data,offset);

        int ssap = ByteReader.unsigned8(data,offset+1);
        int control  = ByteReader.unsigned8(data,offset+2);

        if(dsap != 0xAA || ssap != 0xAA){
            throw new IllegalArgumentException("This is a non LLC/SNAP frame - no ethernetType next");
        }

        StringBuilder organizationCode = new StringBuilder();

        for (int i = offset+3; i < offset+6; i++) {
            organizationCode.append(String.format("%02x", data[i] & 0xFF));
            if (i < offset+6-1) {
                organizationCode.append(":");
            }
        }

        int etherType = ByteReader.unsigned16BigEndian(data,offset+6);

        return LlcSnapHeader.builder()
                .control(control)
                .destinationServiceAccessPoint(dsap)
                .sourceServiceAccessPoint(ssap)
                .etherType(etherType)
                .organizationCode(organizationCode.toString()).build();


    }

}
