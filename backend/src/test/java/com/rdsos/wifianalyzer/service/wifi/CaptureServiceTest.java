package com.rdsos.wifianalyzer.service.wifi;

import com.rdsos.wifianalyzer.model.analyzer.AccessPoint;
import com.rdsos.wifianalyzer.model.RawFrame;
import com.rdsos.wifianalyzer.service.parser.CaptureSource;
import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import com.rdsos.wifianalyzer.service.sniffer.PacketStore;
import com.rdsos.wifianalyzer.util.OuiDatabase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class CaptureServiceTest {

    private static final Path BEACONS_DATA = Path.of("src/test/resources/fixtures/beacons-wpa2.pcap");

    private static final String OUI_LINE = "50-D4-F7   (hex)\t\tTP-LINK TECHNOLOGIES CO.,LTD.\n";

    @Test
    void givenABeaconFrame_ThenTheApIsInTheRegistry(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));
        CaptureService captureService = new CaptureService(registry, mock(ChannelHopper.class), mock(InterfaceService.class), new PacketStore());

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            captureService.processFrame(source.next());
        }

        // processFrame uses the current time, so the snapshot must use it too
        List<AccessPoint> result = registry.snapshot(System.currentTimeMillis());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSsid()).isEqualTo("HomeNetwork");
        assertThat(captureService.getFrames()).isEqualTo(1);
        assertThat(captureService.getParseErrors()).isEqualTo(0);
    }

    @Test
    void givenAFrame_ThenTheDecodedPacketIsInTheStore(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));
        PacketStore packetStore = new PacketStore();
        CaptureService captureService = new CaptureService(registry, mock(ChannelHopper.class), mock(InterfaceService.class), packetStore);

        try(CaptureSource source = new PcapFileReader(BEACONS_DATA)){
            captureService.processFrame(source.next());
        }

        // the first packet gets id 1, and the packet list shows the beacon from it
        assertThat(packetStore.get(1)).isNotNull();
        assertThat(packetStore.get(1).getPacket().getBeacon().getSsid()).isEqualTo("HomeNetwork");
    }

    @Test
    void givenABrokenFrame_ThenItIsCountedAndNoExceptionIsThrown(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));
        CaptureService captureService = new CaptureService(registry, mock(ChannelHopper.class), mock(InterfaceService.class), new PacketStore());

        // linkType 127 (Radiotap), but only 3 bytes: RadioTapParser needs at least 8
        RawFrame brokenFrame = new RawFrame(0, 127, 3, new byte[]{0x00, 0x00, 0x08});

        captureService.processFrame(brokenFrame);

        assertThat(captureService.getFrames()).isEqualTo(1);
        assertThat(captureService.getParseErrors()).isEqualTo(1);
        assertThat(registry.snapshot(System.currentTimeMillis())).isEmpty();
    }

    @Test
    void givenAReplay_ThenItRunsInTheBackgroundUntilStop() throws InterruptedException {

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));
        CaptureService captureService = new CaptureService(registry, mock(ChannelHopper.class), mock(InterfaceService.class), new PacketStore());

        try {
            captureService.startReplay(BEACONS_DATA);

            // startReplay returns at once, the frames are read on the capture thread
            assertThat(captureService.isRunning()).isTrue();
            assertThat(captureService.getSource()).isEqualTo("FILE");
            assertThat(captureService.getSourceName()).isEqualTo("beacons-wpa2.pcap");

            // wait at most 2 seconds for the capture thread to put HomeNetwork in the registry
            waitUntilTheRegistryHasAnAp(registry, 2000);
            assertThat(registry.snapshot(System.currentTimeMillis())).hasSize(1);
        } finally {
            captureService.stop();
        }

        assertThat(captureService.isRunning()).isFalse();
    }

    @Test
    void givenACaptureIsRunning_ThenASecondStartIsRefused(){

        AccessPointRegistry registry = new AccessPointRegistry(new OuiDatabase(new ByteArrayInputStream(OUI_LINE.getBytes())));
        CaptureService captureService = new CaptureService(registry, mock(ChannelHopper.class), mock(InterfaceService.class), new PacketStore());

        try {
            captureService.startReplay(BEACONS_DATA);

            // the API turns IllegalStateException into 409 Conflict
            assertThatThrownBy(() -> captureService.startReplay(BEACONS_DATA))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            captureService.stop();
        }
    }

    // checks the registry every 10 ms until it has an AP or the time is up
    private void waitUntilTheRegistryHasAnAp(AccessPointRegistry registry, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (registry.snapshot(System.currentTimeMillis()).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
