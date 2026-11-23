package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;

public interface CaptureSource extends AutoCloseable{
    RawFrame next();

    @Override
    void close();
}
