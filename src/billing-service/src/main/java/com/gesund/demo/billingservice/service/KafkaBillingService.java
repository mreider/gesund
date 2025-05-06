package com.gesund.demo.billingservice.service;

import com.gesund.demo.billingservice.model.BillingMessage;
import com.gesund.demo.billingservice.model.BillingRecord;
import com.gesund.demo.billingservice.model.BillingRequest;
import com.gesund.demo.billingservice.model.BillingResponse;
import com.gesund.demo.billingservice.repository.BillingRepository;
import com.gesund.demo.billingservice.util.ContextPropagationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "kafka")
public class KafkaBillingService implements BillingService {

    private final BillingRepository billingRepository;
    private final KafkaTemplate<String, BillingMessage> kafkaTemplate;
    
    public KafkaBillingService(BillingRepository billingRepository, 
                              KafkaTemplate<String, BillingMessage> kafkaTemplate) {
        this.billingRepository = billingRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Value("${kafka.topic.billing}")
    private String billingTopic;

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

            sendToKafka(message);
            log.info("Sent billing message to Kafka for transaction ID: {}", transactionId);

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

    private void sendToKafka(BillingMessage message) {
        try {
            log.debug("Sending message to Kafka topic: {}, key: {}", billingTopic, message.getTransactionId().toString());
            
            // Create a producer record so we can access the headers
            ProducerRecord<String, BillingMessage> record = new ProducerRecord<>(
                    billingTopic, 
                    message.getTransactionId().toString(), 
                    message
            );
            
            // Inject the current context into the record headers
            ContextPropagationUtil.injectContextToKafkaHeaders(record);
            
            // Get current span for logging
            Span currentSpan = Span.current();
            log.debug("Injected trace context - TraceId: {}, SpanId: {}", 
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId());
            
            // Send the record with context headers
            kafkaTemplate.send(record);
        } catch (Exception e) {
            log.error("Error sending message to Kafka: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let the caller handle it
        }
    }
}
