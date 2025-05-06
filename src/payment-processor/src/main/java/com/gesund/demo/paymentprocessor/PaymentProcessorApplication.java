package com.gesund.demo.paymentprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentProcessorApplication {

    public static void main(String[] args) {
        // Make sure we don't disable the SDK
        System.setProperty("otel.sdk.disabled", "false");
        
        SpringApplication.run(PaymentProcessorApplication.class, args);
    }
}
