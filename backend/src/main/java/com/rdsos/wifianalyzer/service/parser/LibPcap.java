package com.rdsos.wifianalyzer.service.parser;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The libpcap functions we call from Java through the FFM API (java.lang.foreign), with no JNI and no C code.
 * Each C function is looked up once by name, described by its signature, and wrapped in a Java method of the
 * same name, so the rest of the code calls e.g. {@code LibPcap.nextEx(...)} like any Java method.
 * libpcap only opens the capture and hands out raw bytes; all parsing is ours.
 */
final class LibPcap {

    private LibPcap() {
    }

    /** Debian and Ubuntu ship libpcap 1.x under its old name, libpcap.so.0.8 (there is no libpcap.so.1). */
    private static final String LIBRARY_NAME = "libpcap.so.0.8";

    /** The bridge between Java and C. */
    private static final Linker LINKER = Linker.nativeLinker();

    /** Finds functions by name inside libpcap. The global arena keeps the library loaded until the JVM exits. */
    private static final SymbolLookup LIBPCAP = SymbolLookup.libraryLookup(LIBRARY_NAME, Arena.global());

    // One MethodHandle per C function. ADDRESS = any C pointer, JAVA_INT = a C int.
    // FunctionDescriptor.of(returnType, parameterTypes...), or ofVoid(parameterTypes...) when the C function returns void.

    /** pcap_t *pcap_open_offline(const char *fname, char *errbuf) */
    private static final MethodHandle PCAP_OPEN_OFFLINE = function("pcap_open_offline", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** pcap_t *pcap_create(const char *source, char *errbuf) */
    private static final MethodHandle PCAP_CREATE = function("pcap_create", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** int pcap_set_snaplen(pcap_t *p, int snaplen) */
    private static final MethodHandle PCAP_SET_SNAPLEN = function("pcap_set_snaplen", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /** int pcap_set_promisc(pcap_t *p, int promisc) */
    private static final MethodHandle PCAP_SET_PROMISC = function("pcap_set_promisc", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /** int pcap_set_timeout(pcap_t *p, int to_ms) */
    private static final MethodHandle PCAP_SET_TIMEOUT = function("pcap_set_timeout", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /** int pcap_set_immediate_mode(pcap_t *p, int immediate_mode) */
    private static final MethodHandle PCAP_SET_IMMEDIATE_MODE = function("pcap_set_immediate_mode", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /** int pcap_activate(pcap_t *p) */
    private static final MethodHandle PCAP_ACTIVATE = function("pcap_activate", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** int pcap_datalink(pcap_t *p) */
    private static final MethodHandle PCAP_DATALINK = function("pcap_datalink", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** int pcap_next_ex(pcap_t *p, struct pcap_pkthdr **pkt_header, const u_char **pkt_data) */
    private static final MethodHandle PCAP_NEXT_EX = function("pcap_next_ex", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /** char *pcap_geterr(pcap_t *p) */
    private static final MethodHandle PCAP_GETERR = function("pcap_geterr", FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** void pcap_close(pcap_t *p) */
    private static final MethodHandle PCAP_CLOSE = function("pcap_close", FunctionDescriptor.ofVoid(ADDRESS));

    /** The size of libpcap's error buffer (PCAP_ERRBUF_SIZE in pcap.h). */
    static final int ERROR_BUFFER_SIZE = 256;

    private static MethodHandle function(String name, FunctionDescriptor signature) {
        MemorySegment address = LIBPCAP.find(name)
                .orElseThrow(() -> new IllegalStateException(name + " not found in " + LIBRARY_NAME));
        return LINKER.downcallHandle(address, signature);
    }

    // The Java methods below just call the handles. invokeExact needs the exact types, hence the casts,
    // and it is declared to throw Throwable, which we turn into an unchecked exception.

    static MemorySegment openOffline(MemorySegment fileName, MemorySegment errorBuffer) {
        try {
            return (MemorySegment) PCAP_OPEN_OFFLINE.invokeExact(fileName, errorBuffer);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_open_offline failed", e);
        }
    }

    static MemorySegment create(MemorySegment interfaceName, MemorySegment errorBuffer) {
        try {
            return (MemorySegment) PCAP_CREATE.invokeExact(interfaceName, errorBuffer);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_create failed", e);
        }
    }

    static int setSnaplen(MemorySegment handle, int snapshotLength) {
        try {
            return (int) PCAP_SET_SNAPLEN.invokeExact(handle, snapshotLength);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_set_snaplen failed", e);
        }
    }

    static int setPromisc(MemorySegment handle, int promiscuous) {
        try {
            return (int) PCAP_SET_PROMISC.invokeExact(handle, promiscuous);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_set_promisc failed", e);
        }
    }

    static int setTimeout(MemorySegment handle, int timeoutMillis) {
        try {
            return (int) PCAP_SET_TIMEOUT.invokeExact(handle, timeoutMillis);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_set_timeout failed", e);
        }
    }

    static int setImmediateMode(MemorySegment handle, int immediate) {
        try {
            return (int) PCAP_SET_IMMEDIATE_MODE.invokeExact(handle, immediate);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_set_immediate_mode failed", e);
        }
    }

    static int activate(MemorySegment handle) {
        try {
            return (int) PCAP_ACTIVATE.invokeExact(handle);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_activate failed", e);
        }
    }

    static int datalink(MemorySegment handle) {
        try {
            return (int) PCAP_DATALINK.invokeExact(handle);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_datalink failed", e);
        }
    }

    static int nextEx(MemorySegment handle, MemorySegment headerPointer, MemorySegment dataPointer) {
        try {
            return (int) PCAP_NEXT_EX.invokeExact(handle, headerPointer, dataPointer);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_next_ex failed", e);
        }
    }

    /** The last error of this handle, as a Java string. */
    static String errorMessage(MemorySegment handle) {
        try {
            MemorySegment message = (MemorySegment) PCAP_GETERR.invokeExact(handle);
            // a pointer returned by C has length 0 for Java; tell Java how many bytes it may read
            return message.reinterpret(ERROR_BUFFER_SIZE).getString(0);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_geterr failed", e);
        }
    }

    static void close(MemorySegment handle) {
        try {
            PCAP_CLOSE.invokeExact(handle);
        } catch (Throwable e) {
            throw new IllegalStateException("pcap_close failed", e);
        }
    }
}
