package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.PacketDetail;
import com.rdsos.wifianalyzer.exception.PacketNotFoundException;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PacketController {

    private final PacketStore packetStore;
    private final PacketViewMapper packetViewMapper;

    // called when the user clicks a row in the packet list
    @GetMapping("/api/packets/{id}")
    public PacketDetail packetDetail(@PathVariable long id) {
        StoredPacket stored = packetStore.get(id);
        if (stored == null) {
            throw new PacketNotFoundException(id);
        }
        return packetViewMapper.toDetail(stored);
    }
}
