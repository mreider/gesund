package com.gesund.demo.billingservice.service;

import com.gesund.demo.billingservice.model.BillingRequest;
import com.gesund.demo.billingservice.model.BillingResponse;

public interface BillingService {
    BillingResponse processBilling(BillingRequest request);
}
