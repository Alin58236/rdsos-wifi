package com.rdsos.wifianalyzer.iface;

import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelHopperTest {

    @TempDir
    Path sys;

    private final FakeCommandRunner runner = new FakeCommandRunner();
    private ChannelHopper hopper;

    @BeforeEach
    void setUp() {
        FakeSysfs.addInterface(sys, "wlp4s0", true, 803);
        var properties = FakeSysfs.properties(sys, "rtw88_8822ce");
        hopper = new ChannelHopper(new InterfaceService(runner, properties), properties);
    }

    @AfterEach
    void tearDown() {
        hopper.stop();
    }

    private List<String> channelsSet() {
        return runner.commands.stream().map(List::getLast).toList();
    }

    @Test
    void ticksCycleThroughAllChannelsAndWrapAround() {
        hopper.target("wlp4s0");

        IntStream.range(0, 15).forEach(i -> hopper.tick());

        assertThat(channelsSet()).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "1", "2");
        assertThat(hopper.currentChannel()).isEqualTo(2);
    }

    @Test
    void pinSetsTheChannelOnceAndPausesHopping() {
        hopper.target("wlp4s0");
        hopper.tick();

        hopper.pin(6);
        hopper.tick();
        hopper.tick();

        assertThat(channelsSet()).containsExactly("1", "6");
        assertThat(hopper.pinnedChannel()).contains(6);
        assertThat(hopper.currentChannel()).isEqualTo(6);
    }

    @Test
    void unpinResumesHoppingAfterThePinnedChannel() {
        hopper.target("wlp4s0");
        hopper.pin(6);

        hopper.unpin();
        hopper.tick();

        assertThat(channelsSet()).containsExactly("6", "7");
        assertThat(hopper.pinnedChannel()).isEmpty();
    }

    @Test
    void pinRejectsChannelsOutsideTheList() {
        hopper.target("wlp4s0");

        assertThatThrownBy(() -> hopper.pin(14)).isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void failedChannelSwitchDoesNotStopTheHopper() {
        hopper.target("wlp4s0");
        runner.failWhen(command -> command.getLast().equals("2"));

        hopper.tick();
        hopper.tick();
        hopper.tick();

        assertThat(channelsSet()).containsExactly("1", "2", "3");
    }

    @Test
    void tickWithoutTargetDoesNothing() {
        hopper.tick();

        assertThat(runner.commands).isEmpty();
    }

    @Test
    void startAndStopControlTheScheduler() {
        hopper.start("wlp4s0");
        assertThat(hopper.isRunning()).isTrue();

        hopper.stop();
        assertThat(hopper.isRunning()).isFalse();
    }
}
