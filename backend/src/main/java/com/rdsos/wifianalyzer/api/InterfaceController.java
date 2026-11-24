package com.rdsos.wifianalyzer.api;

import com.rdsos.wifianalyzer.api.dto.ModeRequest;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import com.rdsos.wifianalyzer.service.iface.WifiInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interfaces")
@RequiredArgsConstructor
@Slf4j
public class InterfaceController {

    private final InterfaceService interfaceService;
    private final ChannelHopper channelHopper;

    @GetMapping
    public List<WifiInterface> listInterfaces() {
        return interfaceService.listWirelessInterfaces();
    }

    @PostMapping("/{name}/mode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void switchMode(@PathVariable String name, @RequestBody ModeRequest request) {
        String mode = request.getMode();
        if ("MONITOR".equals(mode)) {
            log.info("[API] Switching to monitor mode");
            interfaceService.switchToMonitor(name);
        } else if ("MANAGED".equals(mode)) {
            log.info("[API] Switching to monitor mode");
            channelHopper.stop();
            interfaceService.switchToManaged(name);
        } else {
            throw new IllegalArgumentException("mode must be MONITOR or MANAGED");
        }
    }
}
