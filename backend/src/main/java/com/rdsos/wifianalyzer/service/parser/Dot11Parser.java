package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.Dot11Header;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.util.List;

public class Dot11Parser {

    public Dot11Header parse(byte[] data, int offset){

        if(offset+10 > data.length){
            throw new IllegalArgumentException("Truncated data segment for 802.11 headder");
        }

        int byte0 = ByteReader.unsigned8(data,offset);
        int byte1 = ByteReader.unsigned8(data,offset+1);

        int type = (byte0 >> 2) & 0x03;
        int subtype = (byte0 >> 4) & 0x0F;
        boolean toDs = (byte1 & 0x01) != 0;
        boolean fromDs = (byte1 & 0x02) != 0;
        boolean protectedFrame = (byte1 & 0x40) != 0;
        String destination = null;
        String source = null;
        String BSSID = null;
        int headerLength = 0;


        if(isControlFrame(type)){
            if(isACK(subtype) || isCTS(subtype)){
                String addr1 = ByteReader.mac(data, offset + 4);
                destination=addr1;
                headerLength=10;
                return new Dot11Header(type, subtype, toDs, fromDs, protectedFrame,
                        List.of(addr1), null, null, destination, headerLength);

            } else if(offset+16 > data.length){
                throw new IllegalArgumentException("This is an incomplete Control Frame");
            } else {
                String addr1 = ByteReader.mac(data, offset + 4);
                String addr2 = ByteReader.mac(data, offset + 10);
                headerLength=16;
                return new Dot11Header(type,subtype,toDs,fromDs,protectedFrame,List.of(addr1,addr2),null,addr2,addr1,headerLength);
            }
        }



        if (offset + 24 > data.length) {
            throw new IllegalArgumentException("Truncated 802.11 management/data header");
        }

        String addr1 = ByteReader.mac(data, offset + 4);
        String addr2 = ByteReader.mac(data, offset + 10);
        String addr3 = ByteReader.mac(data, offset + 16);



        if(!toDs && !fromDs){
            destination = addr1;
            source = addr2;
            BSSID = addr3;
        }else
        if(toDs && !fromDs){
            BSSID=addr1;
            source=addr2;
            destination=addr3;
        }else
        if(!toDs && fromDs){
            destination=addr1;
            BSSID=addr2;
            source=addr3;
        }else
        if(toDs && fromDs){
            //nu stiu ce e RA!!!
        }

        headerLength=24;

        if((type == 2) && (subtype & 0x08) !=0){
            headerLength += 2;
        }

        return new Dot11Header(type,subtype,toDs,fromDs,protectedFrame, List.of(addr1,addr2,addr3),BSSID,source,destination,headerLength);


    }

    private boolean isCTS(int subtype) {
        return subtype==12;
    }

    private boolean isACK(int subtype) {
        return subtype==13;
    }

    private boolean isControlFrame(int type) {
        return type==1;
    }

}
