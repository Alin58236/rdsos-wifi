package com.rdsos.wifianalyzer;

import com.rdsos.wifianalyzer.service.iface.ChannelHopper;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import com.rdsos.wifianalyzer.service.parser.PcapFileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class WifiAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WifiAnalyzerApplication.class, args);
	}

	/** Manual test: monitor mode + channel hopping. Runs only with --spring.profiles.active=manual (needs root). */
	@Bean
	@Profile("manual")
	CommandLineRunner manualMonitorTest(InterfaceService interfaceService, ChannelHopper channelHopper) {
		return args -> {
//			String name = interfaceService.listWirelessInterfaces().getFirst().getName();
//			interfaceService.switchToMonitor(name);
//			channelHopper.start(name);
//
//			while (channelHopper.isRunning()) {
//				log.info("Current channel: {}", channelHopper.currentChannel());
//				Thread.sleep(1000);
//			}
		};
	}
}
