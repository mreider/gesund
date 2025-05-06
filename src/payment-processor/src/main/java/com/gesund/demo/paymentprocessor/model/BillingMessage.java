package com.gesund.demo.paymentprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingMessage implements Serializable {
    private UUID transactionId;
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime timestamp;
    private String messageType;
}
