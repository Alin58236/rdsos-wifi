package com.rdsos.wifianalyzer.service.wifi;

import com.rdsos.wifianalyzer.model.Beacon;
import com.rdsos.wifianalyzer.model.Dot11Header;
import com.rdsos.wifianalyzer.model.RadioTapHeader;
import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.service.parser.BeaconParser;
import com.rdsos.wifianalyzer.service.parser.CaptureSource;
import com.rdsos.wifianalyzer.service.parser.Dot11Parser;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import com.rdsos.wifianalyzer.service.parser.RadioTapParser;
import jakarta.annotation.PreDestroy;
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
    private final RadioTapParser radioTapParser = new RadioTapParser();
    private final Dot11Parser dot11Parser = new Dot11Parser();
    private final BeaconParser beaconParser = new BeaconParser();
    private final AtomicLong frames = new AtomicLong();
    private final AtomicLong parseErrors = new AtomicLong();

    //volatile ca sa vada thread-ul HTTP valorile curente
    private volatile boolean running;
    private volatile String sourceType;
    private volatile String sourceName;
    private volatile Integer linkType;

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
        linkType = frame.getLinkType();

        try{
            if(isMonitorModeFrame(frame)){

                RadioTapHeader radioTapHeader = radioTapParser.parse(frame.getData(),0);
                Dot11Header dot11Header = dot11Parser.parse(frame.getData(),radioTapHeader.getLength());

                if(dot11Header.getType() == 0 && (dot11Header.getSubtype()==8 || dot11Header.getSubtype()==5)){
                    int offset = radioTapHeader.getLength() + dot11Header.getHeaderLength();
                    int end = radioTapHeader.isHasFrameCheckSequence() ? frame.getData().length -4 : frame.getData().length;
                    Beacon beacon = beaconParser.parse(frame.getData(),offset,end);

                    registry.update(frame.getData(),radioTapHeader,dot11Header,beacon,System.currentTimeMillis());
                }

                //maine data frames LLC/SNAP -> IPv4 ->  TCP -> HTTP

            }

            if(isManagedModeFrame(frame)){
                //maine: Ethernet -> IPv4 -> TCP -> HTTP
            }
        } catch (IllegalArgumentException e) {
            parseErrors.incrementAndGet();
        }
    }

    private boolean isMonitorModeFrame(RawFrame frame){
        return frame.getLinkType()==127;
    }


    private boolean isManagedModeFrame(RawFrame frame){
        return frame.getLinkType() ==1;
    }


    public void startLive(String interfaceName) {

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

        sourceType = null;
        sourceName = null;
        linkType = null;
    }

    public boolean isRunning(){
        return running;
    }

    public String getSource(){
        return sourceType;
    }

    public String getSourceName(){
        return sourceName;
    }

    public Integer getLinkType(){
        return linkType;
    }

    public long getFrames(){
        return frames.get();
    }

    public long getParseErrors(){
        return parseErrors.get();
    }
}
