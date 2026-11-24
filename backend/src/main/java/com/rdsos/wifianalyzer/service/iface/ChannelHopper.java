package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.config.WifiProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cycles a monitor-mode interface through the configured channels so beacons from every channel are seen.
 * Pinning a channel pauses the cycle (for sniffing one network); unpinning resumes after the pinned channel.
 */
@Slf4j
@Component
public class ChannelHopper {

    private final InterfaceService interfaceService;
    private final List<Integer> channels;
    private final long intervalMs;

    private ScheduledExecutorService scheduler;
    private String interfaceName;
    private int index = -1;
    private int currentChannel;
    private Integer pinnedChannel;

    public ChannelHopper(InterfaceService interfaceService, WifiProperties properties) {
        this.interfaceService = interfaceService;
        this.channels = List.copyOf(properties.getHopChannels());
        this.intervalMs = properties.getHopIntervalMs();
    }

    public synchronized void start(String interfaceName) {
        stop();
        target(interfaceName);
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("channel-hopper").daemon().factory());
        scheduler.scheduleAtFixedRate(this::tick, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        interfaceName = null;
        pinnedChannel = null;
    }

    public synchronized boolean isRunning() {
        return scheduler != null;
    }

    public synchronized void pin(int channel) {
        interfaceService.requireAllowedChannel(channel);
        if (interfaceName == null) {
            throw new IllegalStateException("Channel hopper is not running");
        }
        interfaceService.setChannel(interfaceName, channel);
        pinnedChannel = channel;
        index = channels.indexOf(channel);
        currentChannel = channel;
        log.info("Successfully pinned {} channel to {}",interfaceName,channel);
    }

    public synchronized void unpin() {
        pinnedChannel = null;
        log.info("Unpinned");
    }

    public synchronized Optional<Integer> pinnedChannel() {
        return Optional.ofNullable(pinnedChannel);
    }

    /** Channel the radio was last tuned to, or 0 before the first hop. */
    public synchronized int currentChannel() {
        return currentChannel;
    }

    /** Selects the interface without starting the scheduler; tests drive {@link #tick()} directly. */
    synchronized void target(String interfaceName) {
        interfaceService.requireWireless(interfaceName);
        this.interfaceName = interfaceName;
        index = -1;
    }

    synchronized void tick() {
        if (interfaceName == null || pinnedChannel != null) {
            return;
        }
        index = (index + 1) % channels.size();
        int channel = channels.get(index);
        try {
            interfaceService.setChannel(interfaceName, channel);
            currentChannel = channel;
        } catch (RuntimeException e) {
            // One failed hop must not kill the scheduler; the next tick tries the next channel.
            log.warn("Hop to channel {} failed: {}", channel, e.getMessage());
        }
    }
}
