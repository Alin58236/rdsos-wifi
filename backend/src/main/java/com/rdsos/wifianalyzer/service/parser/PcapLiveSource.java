package com.rdsos.wifianalyzer.service.parser;

import com.rdsos.wifianalyzer.model.RawFrame;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Frames from libpcap: live from a network interface, or from a .pcap file (used by the tests).
 * libpcap only hands out raw bytes; each one is copied into a RawFrame, exactly like PcapFileReader produces,
 * so the parsers do not care where a frame came from.
 */
public final class PcapLiveSource implements CaptureSource {

    /** Keep whole frames (the largest possible 802.11 or Ethernet frame fits). */
    private static final int SNAPSHOT_LENGTH = 65535;

    /** pcap_next_ex returns at least every 100 ms, so the capture loop can notice stop(). */
    private static final int READ_TIMEOUT_MS = 100;

    // struct pcap_pkthdr on 64-bit Linux: tv_sec (8 bytes), tv_usec (8 bytes), caplen (4 bytes), len (4 bytes)
    private static final int PACKET_HEADER_SIZE = 24;
    private static final int SECONDS_OFFSET = 0;
    private static final int MICROSECONDS_OFFSET = 8;
    private static final int CAPTURED_LENGTH_OFFSET = 16;
    private static final int ORIGINAL_LENGTH_OFFSET = 20;

    // what pcap_next_ex returns
    private static final int FRAME_READ = 1;
    private static final int TIMEOUT_EXPIRED = 0;
    private static final int END_OF_FILE = -2;

    /**
     * Native memory owned by this source, freed all at once in close().
     * Shared, not confined: the source is opened on an HTTP thread but read on the capture thread.
     */
    private final Arena arena;
    /** pcap_t*, libpcap's handle for this capture. */
    private final MemorySegment handle;
    private final int linkType;
    private final MemorySegment headerPointerBox;
    private final MemorySegment dataPointerBox;
    private boolean closed;

    private PcapLiveSource(Arena arena, MemorySegment handle) {
        this.arena = arena;
        this.handle = handle;
        this.linkType = LibPcap.datalink(handle);
        this.headerPointerBox = arena.allocate(ADDRESS);
        this.dataPointerBox = arena.allocate(ADDRESS);
    }

    /** Reads a .pcap file through libpcap (pcap_open_offline). Needs no root, so the tests use it. */
    public static PcapLiveSource openFile(Path file) {
        Arena arena = Arena.ofShared();
        MemorySegment errorBuffer = arena.allocate(LibPcap.ERROR_BUFFER_SIZE);
        MemorySegment fileName = arena.allocateFrom(file.toString());

        MemorySegment handle = LibPcap.openOffline(fileName, errorBuffer);
        if (handle.equals(MemorySegment.NULL)) {
            String message = errorBuffer.getString(0);
            arena.close();
            throw new IllegalArgumentException(message);
        }
        return new PcapLiveSource(arena, handle);
    }

    /**
     * Captures live from an interface. The interface must already be in the wanted mode (InterfaceService
     * switches it); we never call pcap_set_rfmon, which can create a separate monN interface. Needs root.
     */
    public static PcapLiveSource openInterface(String interfaceName) {
        Arena arena = Arena.ofShared();
        MemorySegment errorBuffer = arena.allocate(LibPcap.ERROR_BUFFER_SIZE);
        MemorySegment name = arena.allocateFrom(interfaceName);

        MemorySegment handle = LibPcap.create(name, errorBuffer);
        if (handle.equals(MemorySegment.NULL)) {
            String message = errorBuffer.getString(0);
            arena.close();
            throw new IllegalArgumentException(message);
        }

        LibPcap.setSnaplen(handle, SNAPSHOT_LENGTH);
        LibPcap.setPromisc(handle, 1);
        LibPcap.setTimeout(handle, READ_TIMEOUT_MS);
        LibPcap.setImmediateMode(handle, 1);

        // 0 = started, > 0 = started with a warning, < 0 = failed (e.g. no permission without root)
        int status = LibPcap.activate(handle);
        if (status < 0) {
            String message = LibPcap.errorMessage(handle);
            LibPcap.close(handle);
            arena.close();
            throw new IllegalStateException("Cannot capture on " + interfaceName + ": " + message);
        }
        return new PcapLiveSource(arena, handle);
    }

    /** The next frame, or null on a timeout (live) or at the end of the file (offline). */
    @Override
    public RawFrame next() {
        int result = LibPcap.nextEx(handle, headerPointerBox, dataPointerBox);

        if (result == TIMEOUT_EXPIRED || result == END_OF_FILE) {
            return null;
        }
        if (result != FRAME_READ) {
            throw new IllegalStateException("pcap_next_ex failed: " + LibPcap.errorMessage(handle));
        }

        // follow the pointers libpcap wrote into the boxes; C pointers have length 0 for Java until reinterpret
        MemorySegment header = headerPointerBox.get(ADDRESS, 0).reinterpret(PACKET_HEADER_SIZE);
        long seconds = header.get(JAVA_LONG, SECONDS_OFFSET);
        long microseconds = header.get(JAVA_LONG, MICROSECONDS_OFFSET);
        int capturedLength = header.get(JAVA_INT, CAPTURED_LENGTH_OFFSET);
        long originalLength = Integer.toUnsignedLong(header.get(JAVA_INT, ORIGINAL_LENGTH_OFFSET));

        // copy the bytes now: libpcap reuses the same buffer for the next frame
        byte[] data = dataPointerBox.get(ADDRESS, 0).reinterpret(capturedLength).toArray(JAVA_BYTE);

        return new RawFrame(seconds * 1_000_000 + microseconds, linkType, originalLength, data);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // first libpcap, then the memory it was using
        LibPcap.close(handle);
        arena.close();
    }
}
