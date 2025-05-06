package com.gesund.demo.billingservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private UUID transactionId;
    private String customerId;
    private String productId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
