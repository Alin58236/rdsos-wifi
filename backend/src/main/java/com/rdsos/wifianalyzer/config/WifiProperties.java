package com.rdsos.wifianalyzer.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings under the {@code wifi.} prefix in application.properties. */
@Slf4j
@Data
@ConfigurationProperties("wifi")
public class WifiProperties implements InitializingBean {

    /** The only origin allowed for CORS and WebSocket. */
    private String frontendOrigin = "http://localhost:5173";

    /** Kernel module reloaded before entering monitor mode; blank to skip. */
    private String driverModule = "";

    /** Dwell time per channel while hopping. */
    private long hopIntervalMs = 250;

    /** Channels the hopper cycles through; also the only channels that can be pinned. */
    private List<Integer> hopChannels = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));

    /** sysfs directory listing network interfaces (overridable for tests). */
    private Path sysClassNet = Path.of("/sys/class/net");

    @Override
    public void afterPropertiesSet() {
        if (hopChannels == null || hopChannels.isEmpty()) {
            throw new IllegalArgumentException("wifi.hop-channels must not be empty");
        }
        if (hopIntervalMs < 50) {
            throw new IllegalArgumentException("wifi.hop-interval-ms must be at least 50");
        }
    }

    @PostConstruct
    void verify(){
        log.info("DriverModule "+driverModule);
    }
}
