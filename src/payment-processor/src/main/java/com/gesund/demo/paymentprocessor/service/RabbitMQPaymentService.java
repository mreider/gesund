package com.gesund.demo.paymentprocessor.service;

import com.gesund.demo.paymentprocessor.model.BillingMessage;
import com.gesund.demo.paymentprocessor.model.Payment;
import com.gesund.demo.paymentprocessor.model.PaymentMessage;
import com.gesund.demo.paymentprocessor.repository.PaymentRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "rabbitmq")
public class RabbitMQPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("rabbitmq-payment-processor");
    
    public RabbitMQPaymentService(PaymentRepository paymentRepository, 
                                RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Value("${rabbitmq.queue.payment}")
    private String paymentQueue;

    @Override
    @RabbitListener(queues = "${rabbitmq.queue.billing}")
    @Transactional
    public void processPayment(BillingMessage billingMessage) {
        try {
            log.info("Processing payment for transaction: {}, customer: {}", 
                    billingMessage.getTransactionId(), billingMessage.getCustomerId());
            
            // Create a span for processing this message
            Span processSpan = tracer.spanBuilder("process-payment")
                    .setAttribute("transaction.id", billingMessage.getTransactionId().toString())
                    .setAttribute("customer.id", billingMessage.getCustomerId())
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            
            try (Scope scope = processSpan.makeCurrent()) {
                // Randomly throw an exception (about 5% of the time)
                if (random.nextInt(100) < 5) {
                    log.error("Random payment processing error for transaction: {}", billingMessage.getTransactionId());
                    // Add more detailed logging for debugging
                    log.error("Transaction details: customerId={}, amount={}, currency={}", 
                            billingMessage.getCustomerId(), billingMessage.getAmount(), billingMessage.getCurrency());
                    processSpan.recordException(new RuntimeException("Random payment processing error"));
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

                    sendToRabbitMQ(paymentMessage);
                    log.info("Sent payment message to RabbitMQ for payment ID: {}", paymentId);
                } finally {
                    sendSpan.end();
                }
            } finally {
                processSpan.end();
            }
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let Spring AMQP handle it
        }
    }

    private void sendToRabbitMQ(PaymentMessage message) {
        try {
            log.debug("Sending message to RabbitMQ queue: {}, payment ID: {}", 
                    paymentQueue, message.getPaymentId().toString());
            
            // Create a message post processor to inject context
            MessagePostProcessor contextInjector = msg -> {
                // Get current span for logging
                Span currentSpan = Span.current();
                
                // Inject trace context into message headers
                Map<String, String> contextMap = new HashMap<>();
                GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().inject(
                        Context.current(),
                        contextMap,
                        (carrier, key, value) -> carrier.put(key, value)
                );
                
                // Add all context entries as message headers
                MessageProperties props = msg.getMessageProperties();
                contextMap.forEach(props::setHeader);
                
                log.debug("Injected trace context - TraceId: {}, SpanId: {}", 
                        currentSpan.getSpanContext().getTraceId(),
                        currentSpan.getSpanContext().getSpanId());
                
                return msg;
            };
            
            // Send the message with context headers
            rabbitTemplate.convertAndSend(paymentQueue, message, contextInjector);
        } catch (Exception e) {
            log.error("Error sending message to RabbitMQ: {}", e.getMessage(), e);
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
