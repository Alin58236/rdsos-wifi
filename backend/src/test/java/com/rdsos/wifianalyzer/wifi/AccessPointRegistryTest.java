package com.rdsos.wifianalyzer.wifi;

import com.rdsos.wifianalyzer.model.*;
import com.rdsos.wifianalyzer.service.parser.*;
import com.rdsos.wifianalyzer.service.wifi.AccessPointRegistry;
import com.rdsos.wifianalyzer.util.OuiDatabase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessPointRegistryTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    // frames are only from my router..
    private static final String OUI_LINE = "50-D4-F7   (hex)\t\tTP-LINK TECHNOLOGIES CO.,LTD.\n";

    private static final String ROUTER = "50:d4:f7:03:12:f3";


    @Test
    void givenFirstBeacon_ThenTheApHasAllTheFields(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            addBeacon(registry, source.next().getData(), 1000);
        }

        List<AccessPoint> result = registry.snapshot(1000);

        AccessPoint expected = new AccessPoint(ROUTER,"ArtemisZone",10,2457,-90,"WPA/WPA2-PSK","TP-LINK TECHNOLOGIES CO.,LTD.",1000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expected);
    }

    @Test
    void givenAllFiftyBeacons_ThenThereIsOnlyOneAp(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            RawFrame frame = source.next();
            while(frame != null){
                addBeacon(registry, frame.getData(), 1000);
                frame = source.next();
            }
        }

        assertThat(registry.snapshot(1000)).hasSize(1);
    }

    @Test
    void givenNoBeaconFor30Seconds_ThenTheApIsRemoved(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            addBeacon(registry, source.next().getData(), 0);
        }

        assertThat(registry.snapshot(29000)).hasSize(1);
        assertThat(registry.snapshot(31000)).isEmpty();
    }

    @Test
    void givenBeaconWithoutChannel_ThenChannelComesFromRadiotap(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        //simuleaza frame de pe 2437
        RadioTapHeader radioTap = new RadioTapHeader(8,false,2437,-50);
        Dot11Header dot11 = new Dot11Header(0,8,false,false,false, List.of("ff:ff:ff:ff:ff:ff",ROUTER,ROUTER),ROUTER,ROUTER,"ff:ff:ff:ff:ff:ff",24);
        Beacon beacon = new Beacon(100,false,"test",null, List.of());

        registry.update(new byte[0], radioTap, dot11, beacon, 1000);

        AccessPoint ap = registry.snapshot(1000).get(0);
        assertThat(ap.getChannel()).isEqualTo(6);
        assertThat(ap.getFrequencyMHz()).isEqualTo(2437);
    }

    @Test
    void givenNoChannelAtAll_ThenTheApIsNotAdded(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        RadioTapHeader radioTap = new RadioTapHeader(8,false,null,-50);
        Dot11Header dot11 = new Dot11Header(0,8,false,false,false, List.of("ff:ff:ff:ff:ff:ff",ROUTER,ROUTER),ROUTER,ROUTER,"ff:ff:ff:ff:ff:ff",24);
        Beacon beacon = new Beacon(100,false,"test",null, List.of());

        registry.update(new byte[0], radioTap, dot11, beacon, 1000);

        assertThat(registry.snapshot(1000)).isEmpty();
    }

    @Test
    void givenTwoAps_ThenTheStrongestIsFirst(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));

        String weakBssid = "50:d4:f7:00:00:01";
        String strongBssid = "50:d4:f7:00:00:02";

        Dot11Header weakDot11 = new Dot11Header(0,8,false,false,false, List.of("ff:ff:ff:ff:ff:ff",weakBssid,weakBssid),weakBssid,weakBssid,"ff:ff:ff:ff:ff:ff",24);
        Dot11Header strongDot11 = new Dot11Header(0,8,false,false,false, List.of("ff:ff:ff:ff:ff:ff",strongBssid,strongBssid),strongBssid,strongBssid,"ff:ff:ff:ff:ff:ff",24);

        registry.update(new byte[0], new RadioTapHeader(8,false,2412,-80), weakDot11, new Beacon(100,false,"weak",1, List.of()), 1000);
        registry.update(new byte[0], new RadioTapHeader(8,false,2437,-40), strongDot11, new Beacon(100,false,"strong",6, List.of()), 1000);

        List<AccessPoint> result = registry.snapshot(1000);

        assertThat(result.get(0).getSsid()).isEqualTo("strong");
        assertThat(result.get(1).getSsid()).isEqualTo("weak");
    }

    // radiotap -> 802.11 -> beacon, then give everything to the registry
    private void addBeacon(AccessPointRegistry registry, byte[] data, long nowMillis){
        RadioTapHeader radioTap = new RadioTapParser().parse(data,0);
        Dot11Header dot11 = new Dot11Parser().parse(data,radioTap.getLength());
        int offset = radioTap.getLength() + dot11.getHeaderLength();
        int end = radioTap.isHasFrameCheckSequence() ? data.length - 4 : data.length;
        Beacon beacon = new BeaconParser().parse(data,offset,end);

        registry.update(data, radioTap, dot11, beacon, nowMillis);
    }

}
