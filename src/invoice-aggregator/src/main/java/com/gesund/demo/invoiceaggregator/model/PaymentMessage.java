package com.gesund.demo.invoiceaggregator.model;

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
public class PaymentMessage implements Serializable {
    private UUID transactionId;
    private UUID paymentId;
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String processorReference;
    private LocalDateTime timestamp;
    private String messageType;
}
