package com.rdsos.wifianalyzer;

import com.rdsos.wifianalyzer.service.iface.CommandRunner;
import com.rdsos.wifianalyzer.service.iface.InterfaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.util.List;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class WifiAnalyzerApplication {

	private final InterfaceService interfaceService;
	private final CommandRunner runner;

	public static void main(String[] args) {
		SpringApplication.run(WifiAnalyzerApplication.class, args);
	}


	@Bean
	CommandLineRunner cmdLineRunnerStartup(InterfaceService interfaceService, CommandRunner runner){
		return args ->
		{
            log.info("testing interface service {}", interfaceService.listWirelessInterfaces());
            log.info("testing runner {}", runner.run(List.of("iw", "dev")).getOutput());

		};

	}

}
