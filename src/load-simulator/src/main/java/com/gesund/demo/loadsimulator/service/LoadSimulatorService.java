package com.gesund.demo.loadsimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoadSimulatorService {

    private final RestTemplate restTemplate;
    private final Random random = new Random();

    @Value("${billing.service.url}")
    private String billingServiceUrl;

    @Scheduled(fixedDelayString = "${load.simulator.delay.min:5000}", timeUnit = TimeUnit.MILLISECONDS)
    public void simulateLoad() {
        try {
            // Add random delay between min and max
            int minDelay = Integer.parseInt(System.getProperty("load.simulator.delay.min", "5000"));
            int maxDelay = Integer.parseInt(System.getProperty("load.simulator.delay.max", "7000"));
            int additionalDelay = random.nextInt(maxDelay - minDelay);
            Thread.sleep(additionalDelay);

            // Generate random billing request
            String customerId = "CUST-" + (10000 + random.nextInt(90000));
            String productId = "PROD-" + (100 + random.nextInt(900));
            BigDecimal amount = BigDecimal.valueOf(10 + random.nextInt(990), 2); // Random amount between 10.00 and 999.99
            String currency = "USD";

            Map<String, Object> request = new HashMap<>();
            request.put("customerId", customerId);
            request.put("productId", productId);
            request.put("amount", amount);
            request.put("currency", currency);

            log.info("Sending billing request for customer: {}, product: {}, amount: {}", 
                    customerId, productId, amount);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    billingServiceUrl + "/api/billing", 
                    request, 
                    Map.class);

            log.info("Received response with status: {}, transaction ID: {}", 
                    response.getStatusCode(), response.getBody() != null ? response.getBody().get("transactionId") : "N/A");
        } catch (Exception e) {
            log.error("Error simulating load: {}", e.getMessage(), e);
        }
    }
}
