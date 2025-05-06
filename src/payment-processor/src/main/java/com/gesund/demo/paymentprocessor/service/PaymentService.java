package com.gesund.demo.paymentprocessor.service;

import com.gesund.demo.paymentprocessor.model.BillingMessage;

public interface PaymentService {
    void processPayment(BillingMessage message);
}
