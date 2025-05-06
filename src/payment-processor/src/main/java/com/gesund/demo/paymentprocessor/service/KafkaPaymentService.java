package com.gesund.demo.paymentprocessor.service;

import com.gesund.demo.paymentprocessor.model.BillingMessage;
import com.gesund.demo.paymentprocessor.model.Payment;
import com.gesund.demo.paymentprocessor.model.PaymentMessage;
import com.gesund.demo.paymentprocessor.repository.PaymentRepository;
import com.gesund.demo.paymentprocessor.util.ContextPropagationUtil;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "kafka")
public class KafkaPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentMessage> kafkaTemplate;
    private final Random random = new Random();
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("kafka-payment-processor");
    
    public KafkaPaymentService(PaymentRepository paymentRepository, 
                              KafkaTemplate<String, PaymentMessage> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Value("${kafka.topic.payment}")
    private String paymentTopic;

    @Override
    public void processPayment(BillingMessage billingMessage) {
        // This method is required by the PaymentService interface
        // In practice, it will not be called directly as the Kafka listener
        // will invoke processKafkaRecord instead
        log.warn("Direct processPayment call without Kafka context - context propagation will not work properly");
        
        try {
            processMessageWithContext(billingMessage);
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @KafkaListener(topics = "${kafka.topic.billing}", groupId = "${spring.application.name}")
    @Transactional
    public void processKafkaRecord(ConsumerRecord<String, BillingMessage> record) {
        try {
            BillingMessage billingMessage = record.value();
            
            // Extract context from Kafka headers
            Context extractedContext = ContextPropagationUtil.extractContextFromKafkaRecord(record);
            
            log.info("Processing payment for transaction: {}, customer: {}", 
                    billingMessage.getTransactionId(), billingMessage.getCustomerId());
            
            // Create a child span for processing this message, using the extracted context as parent
            Span processSpan = tracer.spanBuilder("process-payment")
                    .setParent(extractedContext)
                    .setAttribute("transaction.id", billingMessage.getTransactionId().toString())
                    .setAttribute("customer.id", billingMessage.getCustomerId())
                    .setAttribute("kafka.topic", record.topic())
                    .setAttribute("kafka.partition", record.partition())
                    .setAttribute("kafka.offset", record.offset())
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            
            try (Scope scope = processSpan.makeCurrent()) {
                processMessageWithContext(billingMessage);
            } finally {
                processSpan.end();
            }
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let Spring Kafka handle it
        }
    }
    
    private void processMessageWithContext(BillingMessage billingMessage) {

        // Randomly throw an exception (about 5% of the time)
        if (random.nextInt(100) < 5) {
            log.error("Random payment processing error for transaction: {}", billingMessage.getTransactionId());
            // Add more detailed logging for debugging
            log.error("Transaction details: customerId={}, amount={}, currency={}", 
                    billingMessage.getCustomerId(), billingMessage.getAmount(), billingMessage.getCurrency());
            Span.current().recordException(new RuntimeException("Random payment processing error"));
            throw new RuntimeException("Random payment processing error");
        }

        // Create a span for the database operation
        Span dbSpan = tracer.spanBuilder("save-payment")
                .setParent(Context.current())
                .setAttribute("transaction.id", billingMessage.getTransactionId().toString())
                .startSpan();
        
        UUID paymentId;
        Payment payment;
        
        try (Scope dbScope = dbSpan.makeCurrent()) {
            // Create and save payment record
            paymentId = UUID.randomUUID();
            payment = Payment.builder()
                    .transactionId(billingMessage.getTransactionId())
                    .paymentId(paymentId)
                    .customerId(billingMessage.getCustomerId())
                    .productId(billingMessage.getProductId())
                    .amount(billingMessage.getAmount())
                    .currency(billingMessage.getCurrency())
                    .status("PROCESSED")
                    .paymentMethod(determinePaymentMethod(billingMessage.getCustomerId()))
                    .processorReference("REF-" + paymentId.toString().substring(0, 8))
                    .createdAt(LocalDateTime.now())
                    .build();

            paymentRepository.save(payment);
            log.info("Saved payment record with payment ID: {}", paymentId);
        } finally {
            dbSpan.end();
        }

        // Create a span for sending the message
        Span sendSpan = tracer.spanBuilder("send-payment-message")
                .setParent(Context.current())
                .setAttribute("payment.id", paymentId.toString())
                .setAttribute("transaction.id", billingMessage.getTransactionId().toString())
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
        
        try (Scope sendScope = sendSpan.makeCurrent()) {
            // Create and send message
            PaymentMessage paymentMessage = PaymentMessage.builder()
                    .transactionId(billingMessage.getTransactionId())
                    .paymentId(paymentId)
                    .customerId(billingMessage.getCustomerId())
                    .productId(billingMessage.getProductId())
                    .amount(billingMessage.getAmount())
                    .currency(billingMessage.getCurrency())
                    .status("PROCESSED")
                    .paymentMethod(payment.getPaymentMethod())
                    .processorReference(payment.getProcessorReference())
                    .timestamp(LocalDateTime.now())
                    .messageType("PAYMENT_PROCESSED")
                    .build();

            sendToKafka(paymentMessage);
            log.info("Sent payment message to Kafka for payment ID: {}", paymentId);
        } finally {
            sendSpan.end();
        }
    }

    private void sendToKafka(PaymentMessage message) {
        try {
            log.debug("Sending message to Kafka topic: {}, key: {}", paymentTopic, message.getPaymentId().toString());
            
            // Create a producer record so we can access the headers
            ProducerRecord<String, PaymentMessage> record = new ProducerRecord<>(
                    paymentTopic, 
                    message.getPaymentId().toString(), 
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

    private String determinePaymentMethod(String customerId) {
        // Simple logic to determine payment method based on customer ID
        String[] methods = {"CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER"};
        int index = Math.abs(customerId.hashCode() % methods.length);
        return methods[index];
    }
}
