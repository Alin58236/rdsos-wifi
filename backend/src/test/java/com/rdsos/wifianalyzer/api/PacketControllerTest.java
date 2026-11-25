package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// the real mapper, only the store is a mock
@WebMvcTest(PacketController.class)
@Import(PacketViewMapper.class)
class PacketControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PacketStore packetStore;

    @Test
    void returnsTheDetailOfAStoredPacket() {
        DecodedPacket packet = DecodedPacket.builder()
                .timestampMicros(1000)
                .data(new byte[]{0x01, 0x02, (byte) 0xab})
                .build();
        when(packetStore.get(42)).thenReturn(new StoredPacket(42, packet));

        assertThat(mvc.get().uri("/api/packets/42"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.hex").isEqualTo("0102ab");

        assertThat(mvc.get().uri("/api/packets/42"))
                .bodyJson()
                .extractingPath("$.id").isEqualTo(42);
    }

    @Test
    void answers404WhenThePacketHasLeftTheStore() {
        // the store keeps only the last 5000, so an old id from the UI is not an error of the client
        when(packetStore.get(1)).thenReturn(null);

        assertThat(mvc.get().uri("/api/packets/1"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.error").isEqualTo("Packet 1 is no longer in the buffer");
    }

    @Test
    void rejectsAnIdThatIsNotANumber() {
        assertThat(mvc.get().uri("/api/packets/abc")).hasStatus(400);
    }
}
