package com.rdsos.wifianalyzer.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UpdatesWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> clients = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        clients.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session);
    }

    public boolean hasClients() {
        return !clients.isEmpty();
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession client : clients) {
            try {
                if (client.isOpen()) {
                    client.sendMessage(message);
                }
            } catch (IOException e) {
                log.debug("Dropping a WebSocket client: {}", e.getMessage());
                clients.remove(client);
            }
        }
    }
}
