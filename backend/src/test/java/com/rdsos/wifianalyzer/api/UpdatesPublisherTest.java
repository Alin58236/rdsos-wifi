package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.model.AccessPoint;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.wifi.AccessPointRegistry;
import com.rdsos.wifianalyzer.ws.UpdatesWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatesPublisherTest {

    private final AccessPointRegistry registry = mock(AccessPointRegistry.class);
    private final ChannelHopper channelHopper = mock(ChannelHopper.class);
    private final UpdatesWebSocketHandler webSocketHandler = mock(UpdatesWebSocketHandler.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final UpdatesPublisher publisher = new UpdatesPublisher(registry, channelHopper, webSocketHandler, jsonMapper);

    private final AccessPoint artemisZone = new AccessPoint("50:d4:f7:03:12:f3", "ArtemisZone", 10, 2457, -68,
            "WPA/WPA2-PSK", "TP-LINK TECHNOLOGIES CO.,LTD.", 1000);

    @Test
    void sendsStatusAccessPointsAndPacketsAsJson() {
        when(webSocketHandler.hasClients()).thenReturn(true);
        when(registry.snapshot(anyLong())).thenReturn(List.of(artemisZone));
        when(channelHopper.isRunning()).thenReturn(true);
        when(channelHopper.currentChannel()).thenReturn(6);
        when(channelHopper.pinnedChannel()).thenReturn(Optional.empty());

        publisher.publish();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(sent.capture());
        JsonNode message = jsonMapper.readTree(sent.getValue());

        assertThat(message.get("aps").get(0).get("bssid").asString()).isEqualTo("50:d4:f7:03:12:f3");
        assertThat(message.get("aps").get(0).get("ssid").asString()).isEqualTo("ArtemisZone");
        assertThat(message.get("aps").get(0).get("signalDBm").asInt()).isEqualTo(-68);
        assertThat(message.get("status").get("currentChannel").asInt()).isEqualTo(6);
        assertThat(message.get("status").get("pinnedChannel").isNull()).isTrue();
        // "interface" is a Java keyword, so the field has another name in Java but the contract name in JSON
        assertThat(message.get("status").has("interface")).isTrue();
        assertThat(message.get("packets").isArray()).isTrue();
    }

    @Test
    void reportsNoChannelWhenTheHopperIsStopped() {
        when(webSocketHandler.hasClients()).thenReturn(true);
        when(registry.snapshot(anyLong())).thenReturn(List.of());
        when(channelHopper.isRunning()).thenReturn(false);
        when(channelHopper.pinnedChannel()).thenReturn(Optional.empty());

        publisher.publish();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(sent.capture());
        assertThat(jsonMapper.readTree(sent.getValue()).get("status").get("currentChannel").isNull()).isTrue();
    }

    @Test
    void sendsNothingWhenNoClientIsConnected() {
        when(webSocketHandler.hasClients()).thenReturn(false);

        publisher.publish();

        verify(webSocketHandler, never()).broadcast(anyString());
        verify(registry, never()).snapshot(anyLong());
    }
}
