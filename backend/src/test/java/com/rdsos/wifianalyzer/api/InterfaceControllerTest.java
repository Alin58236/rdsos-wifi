package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceMode;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import com.rdsos.wifianalyzer.service.iface.WifiInterface;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(InterfaceController.class)
class InterfaceControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    InterfaceService interfaceService;

    @MockitoBean
    ChannelHopper channelHopper;

    @Test
    void listsTheWirelessInterfaces() {
        when(interfaceService.listWirelessInterfaces())
                .thenReturn(List.of(new WifiInterface("wlp4s0", InterfaceMode.MANAGED)));

        assertThat(mvc.get().uri("/api/interfaces"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("[{\"name\":\"wlp4s0\",\"mode\":\"MANAGED\"}]");
    }

    @Test
    void switchesToMonitorMode() {
        assertThat(mvc.post().uri("/api/interfaces/wlp4s0/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MONITOR\"}"))
                .hasStatus(204);

        verify(interfaceService).switchToMonitor("wlp4s0");
    }

    @Test
    void switchingToManagedStopsTheHopperFirst() {
        assertThat(mvc.post().uri("/api/interfaces/wlp4s0/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MANAGED\"}"))
                .hasStatus(204);

        verify(channelHopper).stop();
        verify(interfaceService).switchToManaged("wlp4s0");
    }

    @Test
    void rejectsAnUnknownMode() {
        assertThat(mvc.post().uri("/api/interfaces/wlp4s0/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OTHER\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.error").isNotNull();

        verify(interfaceService, never()).switchToMonitor("wlp4s0");
    }

    @Test
    void rejectsAnInterfaceThatIsNotWireless() {
        doThrow(new IllegalArgumentException("Not a wireless interface: eth0"))
                .when(interfaceService).switchToMonitor("eth0");

        assertThat(mvc.post().uri("/api/interfaces/eth0/mode")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MONITOR\"}"))
                .hasStatus(400)
                .bodyJson().isLenientlyEqualTo("{\"error\":\"Not a wireless interface: eth0\"}");
    }

    @Test
    void rejectsAMalformedBody() {
        assertThat(mvc.post().uri("/api/interfaces/wlp4s0/mode")
                .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.error").isNotNull();
    }
}
