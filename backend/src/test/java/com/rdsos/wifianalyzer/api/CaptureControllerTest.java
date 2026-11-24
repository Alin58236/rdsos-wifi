package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.service.wifi.CaptureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(CaptureController.class)
class CaptureControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CaptureService captureService;

    @MockitoBean
    ReplayFiles replayFiles;

    @Test
    void startsReplayingAFileFromTheList() {
        Path file = Path.of("fixtures/beacons-wpa2.pcap");
        when(replayFiles.resolve("beacons-wpa2.pcap")).thenReturn(file);

        assertThat(mvc.post().uri("/api/capture/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"FILE\",\"file\":\"beacons-wpa2.pcap\"}"))
                .hasStatus(204);

        verify(captureService).startReplay(file);
    }

    @Test
    void rejectsAFileThatIsNotInTheList() {
        when(replayFiles.resolve("../../etc/shadow")).thenThrow(new IllegalArgumentException("Unknown replay file: ../../etc/shadow"));

        assertThat(mvc.post().uri("/api/capture/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"FILE\",\"file\":\"../../etc/shadow\"}"))
                .hasStatus(400);

        verify(captureService, never()).startReplay(any());
    }

    @Test
    void refusesASecondStartWhileACaptureIsRunning() {
        Path file = Path.of("fixtures/beacons-wpa2.pcap");
        when(replayFiles.resolve("beacons-wpa2.pcap")).thenReturn(file);
        doThrow(new IllegalStateException("A capture is already running")).when(captureService).startReplay(file);

        assertThat(mvc.post().uri("/api/capture/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"FILE\",\"file\":\"beacons-wpa2.pcap\"}"))
                .hasStatus(409)
                .bodyJson().isLenientlyEqualTo("{\"error\":\"A capture is already running\"}");
    }

    @Test
    void startsALiveCaptureOnTheGivenInterface() {
        assertThat(mvc.post().uri("/api/capture/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"LIVE\",\"interface\":\"wlp4s0\"}"))
                .hasStatus(204);

        verify(captureService).startLive("wlp4s0");
    }

    @Test
    void rejectsAnUnknownSource() {
        assertThat(mvc.post().uri("/api/capture/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"USB\"}"))
                .hasStatus(400);
    }

    @Test
    void stopsTheCapture() {
        assertThat(mvc.post().uri("/api/capture/stop")).hasStatus(204);

        verify(captureService).stop();
    }
}
