package com.rdsos.wifianalyzer.service.parser;


import com.rdsos.wifianalyzer.model.sniffer.EthernetHeader;
import com.rdsos.wifianalyzer.util.ByteReader;

public class EthernetHeaderParser {

    private final static int VLAN_TAG = 0x8100;

    public EthernetHeader parse(byte[] data, int offset){


        if(offset + 14 > data.length){
            throw new IllegalArgumentException("Truncated EthernetHeader");
        }

        String destinationMac = ByteReader.mac(data,offset);
        String sourceMac = ByteReader.mac(data, offset + 6);
        int etherType = ByteReader.unsigned16BigEndian(data, offset + 12);
        int length = 14;

        if (hasVlanTag(etherType)) {
            if (offset + 18 > data.length) {
                throw new IllegalArgumentException("Truncated VLAN tag");
            }
            etherType = ByteReader.unsigned16BigEndian(data, offset + 16);   // EtherType-ul real, dupa tag
            length = 18;
        }

        return EthernetHeader.builder()
                .destinationMac(destinationMac)
                .sourceMac(sourceMac)
                .etherType(etherType)
                .headerLengthBytes(length)
                .build();

    }


    private boolean hasVlanTag(int etherType){
        return etherType == VLAN_TAG;
    }

}
