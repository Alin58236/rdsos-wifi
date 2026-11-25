package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.util.ByteReader;

public class HttpPayloadParser {

    public String parse(byte[] data, int offset, int length){


        if(length < 0 || offset + length > data.length){
            throw new IllegalArgumentException("Invalid / non existent payload bytes");
        }

        StringBuilder payload = new StringBuilder();

        for (int i = offset; i < offset + length; i++)   {

            int byteValue= ByteReader.unsigned8(data,i);

            if(isReadable(byteValue)){
                payload.append((char) byteValue);
            }else {
                payload.append('.');
            }
        }

        return payload.toString();

    }


    private boolean isReadable(int value) {
        return (value >= 0x20 && value <= 0x7E) || value == 0x0D || value == 0x0A || value == 0x09;   //char,\r,\n,\t
    }

}
