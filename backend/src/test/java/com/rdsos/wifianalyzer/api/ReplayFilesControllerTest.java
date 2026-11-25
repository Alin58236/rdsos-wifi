package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.util.ReplayFiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WebMvcTest(ReplayFilesController.class)
class ReplayFilesControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ReplayFiles replayFiles;

    @Test
    void listsTheReplayFiles() {
        when(replayFiles.list()).thenReturn(List.of("beacons-wpa2.pcap", "wpa2-data.pcap"));

        assertThat(mvc.get().uri("/api/replay-files"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("[\"beacons-wpa2.pcap\",\"wpa2-data.pcap\"]");
    }
}
