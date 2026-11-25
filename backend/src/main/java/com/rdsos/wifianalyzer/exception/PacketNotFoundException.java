package com.rdsos.wifianalyzer.exception;

/** The UI asked for a packet the store no longer has (it keeps only the last 5000). Answered with 404. */
public class PacketNotFoundException extends RuntimeException {

    public PacketNotFoundException(long id) {
        super("Packet " + id + " is no longer in the buffer");
    }
}
