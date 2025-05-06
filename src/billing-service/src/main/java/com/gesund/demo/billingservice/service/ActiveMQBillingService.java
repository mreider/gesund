package com.gesund.demo.billingservice.service;

import com.gesund.demo.billingservice.model.BillingMessage;
import com.gesund.demo.billingservice.model.BillingRecord;
import com.gesund.demo.billingservice.model.BillingRequest;
import com.gesund.demo.billingservice.model.BillingResponse;
import com.gesund.demo.billingservice.repository.BillingRepository;
import com.gesund.demo.billingservice.util.ContextPropagationUtil;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "activemq")
public class ActiveMQBillingService implements BillingService {

    private final BillingRepository billingRepository;
    private final JmsTemplate jmsTemplate;
    
    public ActiveMQBillingService(BillingRepository billingRepository, 
                                JmsTemplate jmsTemplate) {
        this.billingRepository = billingRepository;
        this.jmsTemplate = jmsTemplate;
    }

    @Value("${activemq.queue.billing}")
    private String billingQueue;

    @Override
    @Transactional
    public BillingResponse processBilling(BillingRequest request) {
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

        sendToActiveMQ(message);
        log.info("Sent billing message to ActiveMQ for transaction ID: {}", transactionId);

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
    }

    private void sendToActiveMQ(BillingMessage message) {
        try {
            log.debug("Sending message to ActiveMQ queue: {}, transaction ID: {}", 
                    billingQueue, message.getTransactionId().toString());
            
            // Create a message post processor to inject context
            MessagePostProcessor contextInjector = ContextPropagationUtil.createContextInjector();
            
            // Get current span for logging
            Span currentSpan = Span.current();
            log.debug("Injecting trace context - TraceId: {}, SpanId: {}", 
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId());
            
            // Send the message with context headers
            jmsTemplate.convertAndSend(billingQueue, message, contextInjector);
        } catch (Exception e) {
            log.error("Error sending message to ActiveMQ: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let the caller handle it
        }
    }
}
