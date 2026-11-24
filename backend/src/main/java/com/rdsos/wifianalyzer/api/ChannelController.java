package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.ChannelRequest;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import lombok.RequiredArgsConstructor;
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
public class ChannelController {

    private final ChannelHopper channelHopper;

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pinChannel(@RequestBody ChannelRequest request) {
        channelHopper.pin(request.getChannel());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpinChannel() {
        channelHopper.unpin();
    }
}
