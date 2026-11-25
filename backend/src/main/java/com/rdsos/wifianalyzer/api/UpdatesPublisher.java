package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.PacketSummary;
import com.rdsos.wifianalyzer.api.dto.Status;
import com.rdsos.wifianalyzer.api.dto.UpdateMessage;
import com.rdsos.wifianalyzer.model.analyzer.AccessPoint;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import com.rdsos.wifianalyzer.service.wifi.AccessPointRegistry;
import com.rdsos.wifianalyzer.service.wifi.CaptureService;
import com.rdsos.wifianalyzer.ws.UpdatesWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Every 200 ms, builds one message with the status, the AP list and the packets decoded since the previous message,
 * and sends it to all WebSocket clients.
 * Batching keeps the browser fast: one message per tick instead of one per packet.
 */
@Component
public class UpdatesPublisher {

    private final AccessPointRegistry registry;
    private final ChannelHopper channelHopper;
    private final CaptureService captureService;
    private final UpdatesWebSocketHandler webSocketHandler;
    private final JsonMapper jsonMapper;
    private final PacketStore packetStore;
    private final PacketViewMapper packetViewMapper;

    private static final int MAX_PACKETS_PER_MESSAGE = 500;

    private long lastSentPacketId = 0;

    public UpdatesPublisher(AccessPointRegistry registry, ChannelHopper channelHopper, CaptureService captureService,
                            UpdatesWebSocketHandler webSocketHandler, JsonMapper jsonMapper,
                            PacketStore packetStore, PacketViewMapper packetViewMapper) {
        this.registry = registry;
        this.channelHopper = channelHopper;
        this.captureService = captureService;
        this.webSocketHandler = webSocketHandler;
        this.jsonMapper = jsonMapper;
        this.packetStore = packetStore;
        this.packetViewMapper = packetViewMapper;
    }

    @Scheduled(fixedRate = 200)
    public void publish() {
        if (!webSocketHandler.hasClients()) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        List<AccessPoint> accessPoints = registry.snapshot(nowMillis);
        UpdateMessage message = new UpdateMessage(currentStatus(), accessPoints, newPackets());
        webSocketHandler.broadcast(jsonMapper.writeValueAsString(message));
    }

    // the packets added to the store since the previous message, oldest first
    private List<PacketSummary> newPackets() {
        List<StoredPacket> stored = packetStore.after(lastSentPacketId, MAX_PACKETS_PER_MESSAGE);

        List<PacketSummary> summaries = new ArrayList<>();
        for (StoredPacket packet : stored) {
            summaries.add(packetViewMapper.toSummary(packet));
        }

        if (!stored.isEmpty()) {
            lastSentPacketId = stored.get(stored.size() - 1).getId();
        }
        return summaries;
    }

    private Status currentStatus() {
        Integer currentChannel = channelHopper.isRunning() ? channelHopper.currentChannel() : null;
        Integer pinnedChannel = channelHopper.pinnedChannel().orElse(null);

        return new Status(
                captureService.isRunning(),
                captureService.getSource(),
                captureService.getSourceName(),
                captureService.getMode(),
                captureService.getLinkType(),
                currentChannel,
                pinnedChannel,
                captureService.getFrames(),
                0,
                captureService.getParseErrors());
    }
}
