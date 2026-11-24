package com.rdsos.wifianalyzer.service.wifi;

import com.rdsos.wifianalyzer.model.AccessPoint;
import com.rdsos.wifianalyzer.model.Beacon;
import com.rdsos.wifianalyzer.model.Dot11Header;
import com.rdsos.wifianalyzer.model.RadioTapHeader;
import com.rdsos.wifianalyzer.util.ChannelMath;
import com.rdsos.wifianalyzer.util.OuiDatabase;
import com.rdsos.wifianalyzer.util.SecurityClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccessPointRegistry {

    private static final long EXPIRY_MILLIS = 30_000;

    private final OuiDatabase ouiDatabase;
    private final Map<String, AccessPoint> aps = new ConcurrentHashMap<>();
    private final ChannelMath channelMath = new ChannelMath();
    private final SecurityClassifier securityClassifier = new SecurityClassifier();



    public void update(byte[] data, RadioTapHeader radioTapHeader, Dot11Header dot11Header, Beacon beacon, long nowMillis){

        String bssid = dot11Header.getBssid();
        String ssid = beacon.getSsid();

        Integer channel = beacon.getChannel();
        if (channel == null && radioTapHeader.getFreqMHz() != null) {
            channel = channelMath.frequencyToChannel(radioTapHeader.getFreqMHz());
        }
        if (channel == null) {
            return;
        }

        Integer frequencyMhz = channelMath.channelToFrequency(channel);
        Integer signalDBm = radioTapHeader.getSignalDBm();
        String security = securityClassifier.classify(data,beacon);
        String vendor = ouiDatabase.vendor(bssid);

        AccessPoint ap = new AccessPoint(bssid,ssid,channel,frequencyMhz,signalDBm,security,vendor,nowMillis);

        aps.put(bssid,ap);
    }


    public List<AccessPoint> snapshot(long nowMillis){

        aps.values().removeIf(ap -> nowMillis - ap.getLastSeenMillis() > EXPIRY_MILLIS);

        List<AccessPoint> result = new ArrayList<>(aps.values());
        result.sort(Comparator.comparing(AccessPoint::getSignalDBm, Comparator.nullsLast(Comparator.reverseOrder())));

        return List.copyOf(result);
    }

}
