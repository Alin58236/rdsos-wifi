package com.rdsos.wifianalyzer.service.wifi;

import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceMode;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import com.rdsos.wifianalyzer.service.parser.CaptureSource;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import com.rdsos.wifianalyzer.service.parser.PcapLiveSource;
import com.rdsos.wifianalyzer.service.sniffer.PacketDecoder;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class CaptureService {

    private final AccessPointRegistry registry;
    private final ChannelHopper channelHopper;
    private final InterfaceService interfaceService;
    private final PacketStore packetStore;
    private final PacketDecoder packetDecoder = new PacketDecoder();
    private final AtomicLong frames = new AtomicLong();
    private final AtomicLong parseErrors = new AtomicLong();

    //volatile ca sa vada thread-ul HTTP valorile curente
    @Getter
    private volatile boolean running;
    private volatile String sourceType;
    @Getter
    private volatile String sourceName;
    @Getter
    private volatile Integer linkType;
    @Getter
    private volatile String mode;

    private Thread captureThread;

    public synchronized void startReplay(Path file){

        log.info("Start replaying from file {}", file);
        if (running) {
            throw new IllegalStateException("A capture is already running");
        }

        sourceType = "FILE";
        sourceName = file.getFileName().toString();
        running = true;
        log.info("Creating the capture thread.");
        captureThread = Thread.ofPlatform().name("capture").daemon().start(() -> {
            try {
                fileReplayLoop(file);
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
        });
    }

    private void fileReplayLoop(Path file) throws InterruptedException {

        while (running) {
            int framesInThisPass = 0;

            try (CaptureSource source = new PcapFileReader(file)) {
                RawFrame frame = source.next();
                while (running && frame != null) {
                    processFrame(frame);
                    framesInThisPass++;
                    frame = source.next();
                    Thread.sleep(100);
                }
            } catch (RuntimeException e) {
                log.warn("Replay of {} stopped: {}", file.getFileName(), e.getMessage());
                running = false;
            }

            if (framesInThisPass == 0) {
                running = false;
            }
        }
    }

    void processFrame(RawFrame frame){

        frames.incrementAndGet();

        try{
            DecodedPacket packet = packetDecoder.decode(frame);
            packetStore.add(packet);

            if(packet.getBeacon() != null){
                registry.update(frame.getData(), packet.getRadioTap(), packet.getDot11(), packet.getBeacon(), System.currentTimeMillis());
            }
        } catch (IllegalArgumentException e) {
            parseErrors.incrementAndGet();
        }
    }


    //needs root -> calls libpcap
    public synchronized void startLive(String interfaceName) {

        if (running) {
            throw new IllegalStateException("A capture is already running");
        }

        InterfaceMode interfaceMode = interfaceService.mode(interfaceName);
        CaptureSource source = PcapLiveSource.openInterface(interfaceName);

        log.info("Start live capture on {} ({} mode)", interfaceName, interfaceMode);

        if (interfaceMode == InterfaceMode.MONITOR) {
            channelHopper.start(interfaceName);
        }

        sourceType = "LIVE";
        sourceName = interfaceName;
        mode = interfaceMode.name();
        running = true;
        log.info("Creating the capture thread.");
        captureThread = Thread.ofPlatform().name("capture").daemon().start(() -> liveLoop(source));
    }


    private void liveLoop(CaptureSource source) {

        try {
            while (running) {
                RawFrame frame = source.next();
                // null = libpcap timeout: no frame in the last 100 ms. Not the end, just check running again.
                if (frame != null) {
                    processFrame(frame);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Live capture stopped: {}", e.getMessage());
            running = false;
        } finally {
            source.close();
        }
    }

    @PreDestroy
    public synchronized void stop() {

        log.info("Stopping the capture thread.");
        running = false;

        if (captureThread != null) {
            try {
                captureThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        channelHopper.stop();

        sourceType = null;
        mode = null;
        sourceName = null;
        linkType = null;
    }

    public String getSource(){
        return sourceType;
    }

    public long getFrames(){
        return frames.get();
    }

    public long getParseErrors(){
        return parseErrors.get();
    }
}
