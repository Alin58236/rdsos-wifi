package com.rdsos.wifianalyzer.util;

import com.rdsos.wifianalyzer.model.Beacon;
import com.rdsos.wifianalyzer.model.IE;

import java.util.ArrayList;
import java.util.List;

public class SecurityClassifier {


    public String classify(byte[] data, Beacon beacon) {
        IE rsn = null;
        IE wpa = null;

        for(IE ie: beacon.getIes()){

            if(ie.getId() == 48 ){
                rsn = ie;
            }

            if (ie.getId() ==221 && isWpaIe(data,ie)){
                wpa=ie;
            }

        }

        if (rsn == null && wpa == null) {
            return beacon.isPrivacy() ? "WEP" : "Open";
        }

        List<Integer> akms;
        if (rsn != null) {
            akms = readAkms(data, rsn.getOffset(), rsn.getOffset() + rsn.getLength());
        } else {
            // WPA incepe cu 00 50 f2 01 mereu in rest e ca rsn
            akms = readAkms(data, wpa.getOffset() + 4, wpa.getOffset() + wpa.getLength());
        }

        boolean psk = akms.contains(2);
        boolean sae = akms.contains(8);
        boolean enterprise = akms.contains(1);
        String suffix = enterprise ? "Enterprise" : "PSK";

        if (rsn != null && wpa != null) {
            return "WPA/WPA2-" + suffix;
        }
        if (rsn != null && psk && sae) {
            return "WPA2/WPA3";
        }
        if (rsn != null && sae) {
            return "WPA3-SAE";
        }
        if (rsn != null) {
            return "WPA2-" + suffix;
        }
        return "WPA-" + suffix;
    }

    private boolean isWpaIe(byte[] data, IE ie) {
        if (ie.getLength() < 4) {
            return false;
        }
        int pos = ie.getOffset();
        return ByteReader.unsigned8(data, pos) == 0x00
                && ByteReader.unsigned8(data, pos + 1) == 0x50
                && ByteReader.unsigned8(data, pos + 2) == 0xf2
                && ByteReader.unsigned8(data, pos + 3) == 0x01;
    }

    private List<Integer> readAkms(byte[] data, int start, int end) {
        List<Integer> akms = new ArrayList<>();
        int pos = start;

        pos += 6;
        if (pos + 2 > end) return akms;

        int pairwiseCount = ByteReader.unsigned16LittleEndian(data, pos);
        pos += 2 + 4 * pairwiseCount;
        if (pos + 2 > end) return akms;

        int akmCount = ByteReader.unsigned16LittleEndian(data, pos);
        pos += 2;
        for (int i = 0; i < akmCount && pos + 4 <= end; i++) {
            akms.add(ByteReader.unsigned8(data, pos + 3));
            pos += 4;
        }
        return akms;
    }
}


