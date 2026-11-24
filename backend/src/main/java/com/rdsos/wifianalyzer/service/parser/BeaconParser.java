package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.Beacon;
import com.rdsos.wifianalyzer.model.IE;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class BeaconParser {

    public Beacon parse(byte[] data, int offset, int end){

        InformationElementParser ieParser = new InformationElementParser();

        if(offset+12 > end){
            throw new IllegalArgumentException("Truncated Beacon");
        }

        int beaconInterval = ByteReader.unsigned16LittleEndian(data,offset+8);
        int capability =  ByteReader.unsigned16LittleEndian(data,offset+10);
        boolean privacy = (capability & 0x0010) != 0;
        String ssid = "";
        Integer channel = null;
        Integer htChannel = null;

        List<IE> ies = ieParser.parse(data,offset+12,end);

        if(!ies.isEmpty()){

            for (IE ie : ies) {

                if (ie.getId() == 0) {
                    ssid = new String(data, ie.getOffset(), ie.getLength(), StandardCharsets.UTF_8);
                }

                if (ie.getId() == 3 && ie.getLength() >= 1) {
                    channel = (ByteReader.unsigned8(data, ie.getOffset()));
                }

                if (ie.getId() == 61 && ie.getLength() >= 1) {
                    htChannel = (ByteReader.unsigned8(data, ie.getOffset()));
                }
            }
        }

        if (channel == null) {
            channel = htChannel;
        }

        return new Beacon(beaconInterval,privacy,ssid,channel,ies);


    }

}
