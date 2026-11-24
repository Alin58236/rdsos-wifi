package com.rdsos.wifianalyzer.util;

import org.junit.jupiter.api.Test;

import java.nio.channels.Channel;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ChannelMathTest {

    @Test
    public void givenValidFrequency_thenReturnCorrectBand(){

        ChannelMath channelMath = new ChannelMath();

        assertThat(channelMath.frequencyToChannel(2457)).isEqualTo(10);
        assertThat(channelMath.frequencyToChannel(2412)).isEqualTo(1);
        assertThat(channelMath.frequencyToChannel(2484)).isEqualTo(14);
        assertThat(channelMath.frequencyToChannel(5180)).isEqualTo(36);
        assertThat(channelMath.frequencyToChannel(1234)).isEqualTo(null);
        assertThat(channelMath.frequencyToChannel(0)).isEqualTo(null);

    }


    @Test
    public void givenValidChannel_ThenReturnValidFrequency(){
        ChannelMath channelMath = new ChannelMath();

        assertThat(channelMath.channelToFrequency(10)).isEqualTo(2457);
        assertThat(channelMath.channelToFrequency(1)).isEqualTo(2412);
        assertThat(channelMath.channelToFrequency(14)).isEqualTo(2484);
        assertThat(channelMath.channelToFrequency(10)).isEqualTo(2457);
    }

}
