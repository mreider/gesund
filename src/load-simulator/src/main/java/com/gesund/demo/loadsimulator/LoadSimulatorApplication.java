package com.gesund.demo.loadsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoadSimulatorApplication {

    public static void main(String[] args) {
        // Make sure we don't disable the SDK
        System.setProperty("otel.sdk.disabled", "false");
        
        SpringApplication.run(LoadSimulatorApplication.class, args);
    }
}
