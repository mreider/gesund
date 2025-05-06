package com.gesund.demo.billingservice.controller;

import com.gesund.demo.billingservice.model.BillingRequest;
import com.gesund.demo.billingservice.model.BillingResponse;
import com.gesund.demo.billingservice.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingService billingService;

    @PostMapping
    public ResponseEntity<BillingResponse> createBilling(@RequestBody BillingRequest request) {
        log.info("Received billing request for customer: {}", request.getCustomerId());
        
        BillingResponse response = billingService.processBilling(request);
        return ResponseEntity.ok(response);
    }
}
