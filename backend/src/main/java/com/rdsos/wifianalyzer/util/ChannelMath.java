package com.rdsos.wifianalyzer.util;

public class ChannelMath {

    public Integer channelToFrequency(int channel){

        if (channel == 14) return 2484;
        if (channel >= 1 && channel <= 13) return 2407 + 5 * channel;
        if (channel >= 32 && channel <= 177) return 5000 + 5 * channel;
        return null;
    }

    public Integer frequencyToChannel(int frequency){


        if (frequency == 2484) return 14;
        if (frequency >= 2412 && frequency <= 2472) return (frequency - 2407) / 5;
        if (frequency >= 5160 && frequency <= 5885) return (frequency - 5000) / 5;
        return null;
    }




}
