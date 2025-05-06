package com.gesund.demo.billingservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingRequest {
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private String currency;
}
