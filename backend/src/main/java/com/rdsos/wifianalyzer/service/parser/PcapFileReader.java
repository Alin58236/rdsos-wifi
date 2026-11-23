package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.util.ByteReader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;


public class PcapFileReader implements CaptureSource{


    private final byte[] file;
    private final boolean bigEndian;
    private final boolean nanoseconds;
    private final int linkType;
    private int position;

    public PcapFileReader(Path path){

        try {
            this.file = Files.readAllBytes(path);
            if(file.length <24 ){
                throw new IllegalArgumentException("Not a pcap file: shorter than the 24-byte global header");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }

        long magic = ByteReader.unsigned32LittleEndian(file, 0);

        if(magic == 0xA1B2C3D4L){
            this.bigEndian = false;
            this.nanoseconds = false;
        }else if(magic == 0xA1B23C4DL){
            this.bigEndian = false;
            this.nanoseconds = true;
        }else if(magic == 0xD4C3B2A1L){
            this.bigEndian = true;
            this.nanoseconds = false;
        }else if(magic == 0x4D3CB2A1L){
            this.bigEndian = true;
            this.nanoseconds = true;
        }else if(magic == 0x0A0D0D0AL){
            throw new IllegalArgumentException("Error: not a pcap");
        }else {
            throw new IllegalArgumentException("Error: unknown mabic byte value");
        }

        this.linkType = (int) (bigEndian ? ByteReader.unsigned32BigEndian(file, 20) : ByteReader.unsigned32LittleEndian(file, 20));
        this.position=24;
    }


    @Override
    public RawFrame next() {

        long tsSec;
        long tsFrac;
        long inclLen;
        long origLen;

        long timestampMicros;
        byte[] data;

        if(position == file.length){
            return null;
        }

        if(position+16>file.length){
            throw new IllegalArgumentException("Truncated record header at offset " + position);
        }else{
            tsSec = bigEndian ? ByteReader.unsigned32BigEndian(file,position) : ByteReader.unsigned32LittleEndian(file,position);
            tsFrac = bigEndian ? ByteReader.unsigned32BigEndian(file,position+4) : ByteReader.unsigned32LittleEndian(file,position+4);
            inclLen = bigEndian ? ByteReader.unsigned32BigEndian(file,position+8) : ByteReader.unsigned32LittleEndian(file,position+8);
            origLen = bigEndian ? ByteReader.unsigned32BigEndian(file,position+12) : ByteReader.unsigned32LittleEndian(file,position+12);
        }

        int start = position+16;
        if (start + inclLen > file.length){
            throw new IllegalArgumentException("Truncated data frame at offset " + position);
        }else {
            data = Arrays.copyOfRange(file, start, start + (int) inclLen);
        }

        timestampMicros = tsSec * 1_000_000 + (nanoseconds ? tsFrac / 1000 : tsFrac);
        RawFrame frame = new RawFrame(timestampMicros, this.linkType, origLen, data);
        position = start + (int) inclLen;

        return frame;

    }

    @Override
    public void close() {

    }


}
