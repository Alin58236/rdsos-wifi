package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@WebMvcTest(ChannelController.class)
class ChannelControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ChannelHopper channelHopper;

    @Test
    void pinsAChannel() {
        assertThat(mvc.put().uri("/api/channel")
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":6}"))
                .hasStatus(204);

        verify(channelHopper).pin(6);
    }

    @Test
    void rejectsAChannelOutsideTheList() {
        doThrow(new IllegalArgumentException("Channel 14 is not one of [1..13]")).when(channelHopper).pin(14);

        assertThat(mvc.put().uri("/api/channel")
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":14}"))
                .hasStatus(400);
    }

    @Test
    void refusesToPinWhenTheHopperIsNotRunning() {
        doThrow(new IllegalStateException("Channel hopper is not running")).when(channelHopper).pin(6);

        assertThat(mvc.put().uri("/api/channel")
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":6}"))
                .hasStatus(409)
                .bodyJson().isLenientlyEqualTo("{\"error\":\"Channel hopper is not running\"}");
    }

    @Test
    void unpinsTheChannel() {
        assertThat(mvc.delete().uri("/api/channel")).hasStatus(204);

        verify(channelHopper).unpin();
    }
}
