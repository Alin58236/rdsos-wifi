package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.model.analyzer.AccessPoint;
import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import com.rdsos.wifianalyzer.service.wifi.AccessPointRegistry;
import com.rdsos.wifianalyzer.service.wifi.CaptureService;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatesPublisherTest {

    private final AccessPointRegistry registry = mock(AccessPointRegistry.class);
    private final ChannelHopper channelHopper = mock(ChannelHopper.class);
    private final UpdatesWebSocketHandler webSocketHandler = mock(UpdatesWebSocketHandler.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final CaptureService captureService = mock(CaptureService.class);

    // the real store and mapper: they are plain classes, and the test checks what really reaches the browser
    private final PacketStore packetStore = new PacketStore();

    private final UpdatesPublisher publisher = new UpdatesPublisher(registry, channelHopper, captureService,
            webSocketHandler, jsonMapper, packetStore, new PacketViewMapper());

    private final AccessPoint artemisZone = new AccessPoint("50:d4:f7:03:12:f3", "HomeNetwork", 10, 2457, -68,
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
        assertThat(message.get("aps").get(0).get("ssid").asString()).isEqualTo("HomeNetwork");
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
    void reportsTheRunningCapture() {
        when(webSocketHandler.hasClients()).thenReturn(true);
        when(registry.snapshot(anyLong())).thenReturn(List.of());
        when(channelHopper.pinnedChannel()).thenReturn(Optional.empty());
        when(captureService.isRunning()).thenReturn(true);
        when(captureService.getSource()).thenReturn("LIVE");
        when(captureService.getSourceName()).thenReturn("wlp4s0");
        when(captureService.getMode()).thenReturn("MONITOR");
        when(captureService.getLinkType()).thenReturn(127);
        when(captureService.getFrames()).thenReturn(50L);
        when(captureService.getParseErrors()).thenReturn(2L);

        publisher.publish();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(sent.capture());
        JsonNode status = jsonMapper.readTree(sent.getValue()).get("status");

        assertThat(status.get("running").asBoolean()).isTrue();
        assertThat(status.get("source").asString()).isEqualTo("LIVE");
        assertThat(status.get("interface").asString()).isEqualTo("wlp4s0");
        assertThat(status.get("mode").asString()).isEqualTo("MONITOR");
        assertThat(status.get("linkType").asInt()).isEqualTo(127);
        assertThat(status.get("frames").asLong()).isEqualTo(50);
        assertThat(status.get("parseErrors").asLong()).isEqualTo(2);
    }

    @Test
    void sendsTheNewPacketsAsSummaries() {
        givenAClientAndNoAps();
        packetStore.add(packetWithTimestamp(100));
        packetStore.add(packetWithTimestamp(200));

        publisher.publish();

        JsonNode packets = lastSentMessage().get("packets");
        assertThat(packets.size()).isEqualTo(2);
        assertThat(packets.get(0).get("id").asLong()).isEqualTo(1);
        assertThat(packets.get(0).get("timestampMicros").asLong()).isEqualTo(100);
        assertThat(packets.get(1).get("id").asLong()).isEqualTo(2);
    }

    @Test
    void sendsEachPacketOnlyOnce() {
        givenAClientAndNoAps();
        packetStore.add(packetWithTimestamp(100));
        publisher.publish();

        packetStore.add(packetWithTimestamp(200));
        publisher.publish();

        // the second message has only the packet added after the first one
        JsonNode packets = lastSentMessage().get("packets");
        assertThat(packets.size()).isEqualTo(1);
        assertThat(packets.get(0).get("id").asLong()).isEqualTo(2);
    }

    @Test
    void sendsAtMost500PacketsPerMessageAndTheRestInTheNextOne() {
        givenAClientAndNoAps();
        for (int i = 0; i < 600; i++) {
            packetStore.add(packetWithTimestamp(i));
        }

        publisher.publish();
        assertThat(lastSentMessage().get("packets").size()).isEqualTo(500);

        publisher.publish();
        JsonNode packets = lastSentMessage().get("packets");
        assertThat(packets.size()).isEqualTo(100);
        assertThat(packets.get(0).get("id").asLong()).isEqualTo(501);
    }

    @Test
    void sendsNothingWhenNoClientIsConnected() {
        when(webSocketHandler.hasClients()).thenReturn(false);

        publisher.publish();

        verify(webSocketHandler, never()).broadcast(anyString());
        verify(registry, never()).snapshot(anyLong());
    }

    private void givenAClientAndNoAps() {
        when(webSocketHandler.hasClients()).thenReturn(true);
        when(registry.snapshot(anyLong())).thenReturn(List.of());
        when(channelHopper.pinnedChannel()).thenReturn(Optional.empty());
    }

    // the message given to broadcast() by the latest publish()
    private JsonNode lastSentMessage() {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler, atLeastOnce()).broadcast(sent.capture());
        return jsonMapper.readTree(sent.getValue());
    }

    private DecodedPacket packetWithTimestamp(long timestampMicros) {
        return DecodedPacket.builder()
                .timestampMicros(timestampMicros)
                .data(new byte[]{1, 2, 3})
                .build();
    }
}
