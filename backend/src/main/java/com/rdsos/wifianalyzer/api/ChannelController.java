package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.ChannelRequest;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
@Slf4j
public class ChannelController {

    private final ChannelHopper channelHopper;

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pinChannel(@RequestBody ChannelRequest request) {
        log.info("[API] Pinning channel to "+request.getChannel());
        channelHopper.pin(request.getChannel());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpinChannel() {
        log.info("[API] Unpinning...");
        channelHopper.unpin();
    }
}
