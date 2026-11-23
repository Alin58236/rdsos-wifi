package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.IE;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.util.ArrayList;
import java.util.List;

public class InformationElementParser {

    public List<IE> parse(byte[] data, int offset, int end){

        int id;
        int length;
        List<IE> ies = new ArrayList<>();
        int pos = offset;

        while (pos+2 <= end){
            id = ByteReader.unsigned8(data,pos);
            length = ByteReader.unsigned8(data,pos+1);

            if(pos+2+length >end ){
                throw new IllegalArgumentException("IE is truncated");
            }
            ies.add(new IE(id,pos+2,length));
            pos=pos+2+length;
        }

        return ies;
    }

}
