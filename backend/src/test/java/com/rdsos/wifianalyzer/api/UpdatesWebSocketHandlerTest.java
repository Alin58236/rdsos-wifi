package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.ws.UpdatesWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatesWebSocketHandlerTest {

    private final UpdatesWebSocketHandler handler = new UpdatesWebSocketHandler();

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void sendsTheMessageToEveryConnectedClient() throws IOException {
        WebSocketSession first = openSession();
        WebSocketSession second = openSession();
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        handler.broadcast("{\"aps\":[]}");

        verify(first).sendMessage(new TextMessage("{\"aps\":[]}"));
        verify(second).sendMessage(new TextMessage("{\"aps\":[]}"));
    }

    @Test
    void stopsSendingAfterTheClientDisconnects() throws IOException {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        handler.broadcast("{}");

        verify(session, never()).sendMessage(new TextMessage("{}"));
        assertThat(handler.hasClients()).isFalse();
    }

    @Test
    void dropsAClientWhoseConnectionBroke() throws IOException {
        WebSocketSession broken = openSession();
        doThrow(new IOException("broken pipe")).when(broken).sendMessage(new TextMessage("{}"));
        handler.afterConnectionEstablished(broken);

        handler.broadcast("{}");
        handler.broadcast("{}");

        // the first send fails and removes the client, so the second broadcast does not try again
        verify(broken, times(1)).sendMessage(new TextMessage("{}"));
        assertThat(handler.hasClients()).isFalse();
    }
}
