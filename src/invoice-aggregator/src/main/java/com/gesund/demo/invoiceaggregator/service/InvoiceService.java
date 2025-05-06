package com.gesund.demo.invoiceaggregator.service;

import com.gesund.demo.invoiceaggregator.model.PaymentMessage;

public interface InvoiceService {
    void processPayment(PaymentMessage message);
}
