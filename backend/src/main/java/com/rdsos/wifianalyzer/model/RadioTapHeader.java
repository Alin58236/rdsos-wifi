package com.rdsos.wifianalyzer.model;

import lombok.Value;

@Value
public class RadioTapHeader {
     int length;
     boolean hasFrameCheckSequence;
     Integer freqMHz;
     Integer signalDBm;
}
