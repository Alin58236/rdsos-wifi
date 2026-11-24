package com.rdsos.wifianalyzer.config;

import com.rdsos.wifianalyzer.ws.UpdatesWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers /ws/updates (only the frontend origin may connect) and turns on @Scheduled for the publisher. */
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private final UpdatesWebSocketHandler updatesHandler;
    private final String frontendOrigin;

    public WebSocketConfig(UpdatesWebSocketHandler updatesHandler, @Value("${wifi.frontend-origin}") String frontendOrigin) {
        this.updatesHandler = updatesHandler;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(updatesHandler, "/ws/updates").setAllowedOrigins(frontendOrigin);
    }
}
