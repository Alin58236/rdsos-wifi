package com.rdsos.wifianalyzer.service.sniffer;

import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PacketStoreTest {

    @Test
    void givenTwoPackets_ThenTheyGetIds1And2(){

        PacketStore store = new PacketStore();

        long firstId = store.add(packetWithTimestamp(100));
        long secondId = store.add(packetWithTimestamp(200));

        assertThat(firstId).isEqualTo(1);
        assertThat(secondId).isEqualTo(2);
    }

    @Test
    void givenAStoredPacket_ThenGetByIdReturnsIt(){

        PacketStore store = new PacketStore();
        DecodedPacket packet = packetWithTimestamp(100);

        long id = store.add(packet);
        StoredPacket result = store.get(id);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getPacket()).isEqualTo(packet);
    }

    @Test
    void givenAnUnknownId_ThenGetReturnsNull(){

        PacketStore store = new PacketStore();
        store.add(packetWithTimestamp(100));

        // the controller turns null into a 404
        assertThat(store.get(99)).isNull();
    }

    @Test
    void givenMoreThan5000Packets_ThenTheOldestOneIsRemoved(){

        PacketStore store = new PacketStore();

        for(int i = 1; i <= 5001; i++){
            store.add(packetWithTimestamp(i));
        }

        // only the last 5000 are kept, so id 1 is gone and 2..5001 are still there
        assertThat(store.get(1)).isNull();
        assertThat(store.get(2)).isNotNull();
        assertThat(store.get(5001)).isNotNull();
    }

    @Test
    void givenThreePackets_ThenAfter0ReturnsAllOfThemOldestFirst(){

        PacketStore store = new PacketStore();
        store.add(packetWithTimestamp(100));
        store.add(packetWithTimestamp(200));
        store.add(packetWithTimestamp(300));

        List<StoredPacket> result = store.after(0, 500);

        assertThat(result).extracting(StoredPacket::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void givenThreePackets_ThenAfter2ReturnsOnlyTheNewOne(){

        PacketStore store = new PacketStore();
        store.add(packetWithTimestamp(100));
        store.add(packetWithTimestamp(200));
        store.add(packetWithTimestamp(300));

        // the WebSocket publisher already sent 1 and 2 in the previous message
        List<StoredPacket> result = store.after(2, 500);

        assertThat(result).extracting(StoredPacket::getId).containsExactly(3L);
    }

    @Test
    void givenMorePacketsThanTheMax_ThenOnlyTheFirstMaxAreReturned(){

        PacketStore store = new PacketStore();
        store.add(packetWithTimestamp(100));
        store.add(packetWithTimestamp(200));
        store.add(packetWithTimestamp(300));

        List<StoredPacket> result = store.after(0, 2);

        // the third one is sent in the next message
        assertThat(result).extracting(StoredPacket::getId).containsExactly(1L, 2L);
    }

    @Test
    void givenNoNewPackets_ThenAfterReturnsAnEmptyList(){

        PacketStore store = new PacketStore();
        store.add(packetWithTimestamp(100));

        assertThat(store.after(1, 500)).isEmpty();
    }

    private DecodedPacket packetWithTimestamp(long timestampMicros){
        return DecodedPacket.builder()
                .timestampMicros(timestampMicros)
                .data(new byte[]{1, 2, 3})
                .build();
    }
}
