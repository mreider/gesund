package com.gesund.demo.billingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BillingServiceApplication {

    public static void main(String[] args) {
        // Make sure we don't disable the SDK
        System.setProperty("otel.sdk.disabled", "false");
        
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
