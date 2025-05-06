package com.gesund.demo.billingservice.service;

import com.gesund.demo.billingservice.model.BillingMessage;
import com.gesund.demo.billingservice.model.BillingRecord;
import com.gesund.demo.billingservice.model.BillingRequest;
import com.gesund.demo.billingservice.model.BillingResponse;
import com.gesund.demo.billingservice.repository.BillingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "rabbitmq")
public class RabbitMQBillingService implements BillingService {

    private final BillingRepository billingRepository;
    private final RabbitTemplate rabbitTemplate;
    
    public RabbitMQBillingService(BillingRepository billingRepository, 
                                RabbitTemplate rabbitTemplate) {
        this.billingRepository = billingRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Value("${rabbitmq.queue.billing:billing.queue}")
    private String billingQueue;

    @Override
    @Transactional
    public BillingResponse processBilling(BillingRequest request) {
        try {
            log.info("Processing billing request for customer: {}, product: {}", 
                    request.getCustomerId(), request.getProductId());

            // Create and save billing record
            UUID transactionId = UUID.randomUUID();
            BillingRecord billingRecord = BillingRecord.builder()
                    .transactionId(transactionId)
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            billingRepository.save(billingRecord);
            log.info("Saved billing record with transaction ID: {}", transactionId);

            // Create and send message
            BillingMessage message = BillingMessage.builder()
                    .transactionId(transactionId)
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status("PENDING")
                    .timestamp(LocalDateTime.now())
                    .messageType("BILLING_CREATED")
                    .build();

            sendToRabbitMQ(message);
            log.info("Sent billing message to RabbitMQ for transaction ID: {}", transactionId);

            // Create and return response
            return BillingResponse.builder()
                    .transactionId(transactionId)
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status("PENDING")
                    .createdAt(billingRecord.getCreatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Error processing billing request: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let the caller handle it
        }
    }

    private void sendToRabbitMQ(BillingMessage message) {
        try {
            log.debug("Sending message to RabbitMQ queue: {}, transaction ID: {}", 
                    billingQueue, message.getTransactionId().toString());
            rabbitTemplate.convertAndSend(billingQueue, message);
        } catch (Exception e) {
            log.error("Error sending message to RabbitMQ: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let the caller handle it
        }
    }
}
