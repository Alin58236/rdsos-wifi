package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.config.WifiProperties;
import com.rdsos.wifianalyzer.exception.CommandFailedException;
import com.rdsos.wifianalyzer.service.iface.model.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lists wireless interfaces and switches them between managed and monitor mode with ip/iw/nmcli.
 * Every interface name is checked against sysfs before it reaches a command.
 */
@Slf4j
@Service
public class InterfaceService {

    /** Linux interface names: at most 15 chars (IFNAMSIZ - 1), no slashes or whitespace. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,15}");
    private static final int REAPPEAR_ATTEMPTS = 50;
    private static final long RETRY_DELAY_MS = 200;

    private final CommandRunner runner;
    private final WifiProperties properties;
    private final Path sysClassNet;

    public InterfaceService(CommandRunner runner, WifiProperties properties) {
        this.runner = runner;
        this.properties = properties;
        this.sysClassNet = properties.getSysClassNet();
    }

    public List<WifiInterface> listWirelessInterfaces() {
        try (Stream<Path> entries = Files.list(sysClassNet)) {
            return entries
                    .filter(dir -> Files.isDirectory(dir.resolve("phy80211")))
                    .map(dir -> new WifiInterface(dir.getFileName().toString(), readMode(dir)))
                    .sorted(Comparator.comparing(WifiInterface::getName))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + sysClassNet, e);
        }
    }

    public InterfaceMode mode(String name) {
        requireWireless(name);
        return readMode(sysClassNet.resolve(name));
    }

    /**
     * Reloads the driver (if configured), takes the interface away from NetworkManager and sets monitor type.
     * The reload is needed on the RTL8822CE: without it the radio stays on the 5 GHz band of the boot connection.
     */
    public void switchToMonitor(String name) {
        requireWireless(name);
        String module = properties.getDriverModule();
        boolean reload = module != null && !module.isBlank();
        if (reload) {
            runner.runChecked(List.of("modprobe", "-r", module));
            runner.runChecked(List.of("modprobe", module));
            awaitInterface(name);
        }
        List<String> unmanage = List.of("nmcli", "dev", "set", name, "managed", "no");
        if (reload) {
            // NetworkManager needs a moment to discover the re-registered device.
            runWithRetry(unmanage);
        } else {
            runner.runChecked(unmanage);
        }
        runner.runChecked(List.of("ip", "link", "set", name, "down"));
        runner.runChecked(List.of("iw", "dev", name, "set", "type", "monitor"));
        runner.runChecked(List.of("ip", "link", "set", name, "up"));
        log.info("{} is in monitor mode", name);
    }

    public void switchToManaged(String name) {
        requireWireless(name);
        runner.runChecked(List.of("ip", "link", "set", name, "down"));
        runner.runChecked(List.of("iw", "dev", name, "set", "type", "managed"));
        runner.runChecked(List.of("ip", "link", "set", name, "up"));
        runner.runChecked(List.of("nmcli", "dev", "set", name, "managed", "yes"));
        log.info("{} is back in managed mode", name);
    }

    public void setChannel(String name, int channel) {
        requireAllowedChannel(channel);
        requireWireless(name);
        runner.runChecked(List.of("iw", "dev", name, "set", "channel", Integer.toString(channel)));
    }

    public void requireAllowedChannel(int channel) {
        if (!properties.getHopChannels().contains(channel)) {
            throw new IllegalArgumentException("Channel " + channel + " is not one of " + properties.getHopChannels());
        }
    }

    public void requireWireless(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid interface name");
        }
        if (!Files.isDirectory(sysClassNet.resolve(name).resolve("phy80211"))) {
            throw new IllegalArgumentException("Not a wireless interface: " + name);
        }
    }

    private InterfaceMode readMode(Path interfaceDir) {
        try {
            int type = Integer.parseInt(Files.readString(interfaceDir.resolve("type")).strip());
            return InterfaceMode.fromSysfsType(type);
        } catch (IOException | NumberFormatException e) {
            return InterfaceMode.OTHER;
        }
    }

    private void awaitInterface(String name) {
        for (int i = 0; i < REAPPEAR_ATTEMPTS; i++) {
            if (Files.isDirectory(sysClassNet.resolve(name).resolve("phy80211"))) {
                return;
            }
            sleep();
        }
        throw new IllegalStateException(name + " did not reappear after reloading " + properties.getDriverModule());
    }

    private void runWithRetry(List<String> command) {
        CommandResult result = runner.run(command);
        for (int i = 1; i < REAPPEAR_ATTEMPTS && result.getExitCode() != 0; i++) {
            sleep();
            result = runner.run(command);
        }
        if (result.getExitCode() != 0) {
            throw new CommandFailedException(command, result);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the interface", e);
        }
    }
}
