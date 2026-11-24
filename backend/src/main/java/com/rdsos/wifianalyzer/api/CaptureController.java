package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.CaptureStartRequest;
import com.rdsos.wifianalyzer.service.wifi.CaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/** Starts a capture (live from an interface, or a replay of a .pcap file) and stops it. */
@RestController
@RequestMapping("/api/capture")
@RequiredArgsConstructor
@Slf4j
public class CaptureController {

    private final CaptureService captureService;
    private final ReplayFiles replayFiles;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startCapture(@RequestBody CaptureStartRequest request) {

        if ("FILE".equals(request.getSource())) {
            log.info("[API] Starting the capture in {} mode",request.getSource());
            Path file = replayFiles.resolve(request.getFile());
            captureService.startReplay(file);
        } else if ("LIVE".equals(request.getSource())) {
            log.info("[API] Starting the capture in {} mode",request.getSource());
            captureService.startLive(request.getInterfaceName());
        } else {
            throw new IllegalArgumentException("source must be LIVE or FILE");
        }
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopCapture() {
        log.info("[API] Stopping the capture!");
        captureService.stop();
    }
}
