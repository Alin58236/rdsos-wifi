package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.exception.CommandFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceServiceTest {

    @TempDir
    Path sys;

    private final FakeCommandRunner runner = new FakeCommandRunner();
    private InterfaceService service;

    @BeforeEach
    void setUp() {
        FakeSysfs.addInterface(sys, "wlp4s0", true, 1);
        FakeSysfs.addInterface(sys, "enp2s0", false, 1);
        service = new InterfaceService(runner, FakeSysfs.properties(sys, "rtw88_8822ce"));
    }

    @Test
    void listsOnlyWirelessInterfacesWithTheirMode() {
        FakeSysfs.addInterface(sys, "wlan1", true, 803);

        assertThat(service.listWirelessInterfaces()).containsExactly(
                new WifiInterface("wlan1", InterfaceMode.MONITOR),
                new WifiInterface("wlp4s0", InterfaceMode.MANAGED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"enp2s0", "wlan9", "../wlp4s0", "wlp4s0; reboot", "", "averyveryverylongname"})
    void rejectsNamesThatAreNotWirelessInterfaces(String name) {
        assertThatThrownBy(() -> service.switchToMonitor(name))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void switchToMonitorReloadsDriverThenSetsMonitorType() {
        service.switchToMonitor("wlp4s0");

        assertThat(runner.joined()).containsExactly(
                "modprobe -r rtw88_8822ce",
                "modprobe rtw88_8822ce",
                "nmcli dev set wlp4s0 managed no",
                "ip link set wlp4s0 down",
                "iw dev wlp4s0 set type monitor",
                "ip link set wlp4s0 up");
    }

    @Test
    void switchToMonitorSkipsDriverReloadWhenNoModuleConfigured() {
        service = new InterfaceService(runner, FakeSysfs.properties(sys, ""));

        service.switchToMonitor("wlp4s0");

        assertThat(runner.joined()).first().isEqualTo("nmcli dev set wlp4s0 managed no");
        assertThat(runner.joined()).noneMatch(command -> command.startsWith("modprobe"));
    }

    @Test
    void switchToManagedRestoresTypeAndHandsInterfaceBackToNetworkManager() {
        service.switchToManaged("wlp4s0");

        assertThat(runner.joined()).containsExactly(
                "ip link set wlp4s0 down",
                "iw dev wlp4s0 set type managed",
                "ip link set wlp4s0 up",
                "nmcli dev set wlp4s0 managed yes");
    }

    @Test
    void failingCommandStopsTheSequence() {
        runner.failWhen(command -> command.contains("monitor"));

        assertThatThrownBy(() -> service.switchToMonitor("wlp4s0"))
                .isInstanceOf(CommandFailedException.class)
                .hasMessageContaining("iw dev wlp4s0 set type monitor");
        assertThat(runner.joined()).last().isEqualTo("iw dev wlp4s0 set type monitor");
    }

    @Test
    void setChannelRunsIw() {
        service.setChannel("wlp4s0", 6);

        assertThat(runner.commands).containsExactly(List.of("iw", "dev", "wlp4s0", "set", "channel", "6"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 14, 36, -1})
    void setChannelRejectsChannelsOutsideTheConfiguredList(int channel) {
        assertThatThrownBy(() -> service.setChannel("wlp4s0", channel))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.commands).isEmpty();
    }
}
