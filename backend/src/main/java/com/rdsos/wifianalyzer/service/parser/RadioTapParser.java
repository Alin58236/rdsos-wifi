package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RadioTapHeader;
import com.rdsos.wifianalyzer.util.ByteReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RadioTapParser {

    private static final int[] SIZE_TABLE = {8 , 1 , 1 , 4  ,2  ,1  ,1  ,2  ,2  ,2  ,1  ,1 , 1 , 1 , 2 , 2 , 1 , 1 , 8 , 3 , 8 ,12 ,12 ,12 ,12 , 0  ,1  ,4};
    private static final int[] ALIGN_TABLE = {8,  1 , 1  ,2  ,1  ,1  ,1,  2,  2,  2 , 1 , 1 , 1  ,1  ,2 , 2,  1,  1 , 4  ,1 , 4,  2 , 8 , 2 , 2 , 0  ,1  ,2};

    public RadioTapHeader parse(byte[] data, int offset){

        if(offset + 8 > data.length){
            throw new IllegalArgumentException("The RatioTapHeader is too short. Probably not complete");
        }

        int version = ByteReader.unsigned8(data,offset);

        if (version!=0){
            throw new IllegalArgumentException("Version must always be 0");
        }

        int length = ByteReader.unsigned16LittleEndian(data,offset+2);

        if(offset+length > data.length){
            throw new IllegalArgumentException("The RadioTapHeader is truncated");
        }

        long present0 = ByteReader.unsigned32LittleEndian(data, offset + 4);

        int dataStartPos = offset + 4;

        //parcurg pana termin words
        while (checkIfBit31IsSet(data,dataStartPos)){
            dataStartPos += 4;
            if (dataStartPos + 4 > offset + length) {
                throw new IllegalArgumentException("Present words run past the RadioTap length");
            }
        }
        dataStartPos += 4;

        int position= dataStartPos;

        boolean hasFcs = false;
        Integer frequency = null;
        Integer signal = null;

        for(int bit = 0; bit < SIZE_TABLE.length; bit++){

            if((present0 & (1L << bit))==0){
                continue;
            }
            if(SIZE_TABLE[bit] == 0 ){
                break;
            }

            int rel = position-offset;
            rel = (rel + ALIGN_TABLE[bit] -1) / ALIGN_TABLE[bit] * ALIGN_TABLE[bit];
            position=offset+rel;

            if (bit == 1) {
                hasFcs = hasFcsFlag(data, position);
            } else if (bit == 3) {
                frequency = ByteReader.unsigned16LittleEndian(data, position);
            } else if (bit == 5) {
                signal = ByteReader.signed8(data, position);
            }
            position+=SIZE_TABLE[bit];

        }

        return new RadioTapHeader(length,hasFcs,frequency,signal);

    }



    private boolean checkIfBit31IsSet(byte[] data, int startPosition){
        return ((ByteReader.unsigned32LittleEndian(data, startPosition) & (1L << 31)) != 0);
    }

    private boolean hasFcsFlag(byte[] data, int position)
    {
        return (ByteReader.unsigned8(data,position) & 0x10) != 0;
    }
}
