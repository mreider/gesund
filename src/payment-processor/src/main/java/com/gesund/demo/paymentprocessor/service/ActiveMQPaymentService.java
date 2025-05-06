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
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "activemq")
public class ActiveMQPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final JmsTemplate jmsTemplate;
    private final Random random = new Random();
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("activemq-payment-processor");
    
    @Autowired
    private MessageConverter messageConverter;
    
    public ActiveMQPaymentService(PaymentRepository paymentRepository, 
                                JmsTemplate jmsTemplate) {
        this.paymentRepository = paymentRepository;
        this.jmsTemplate = jmsTemplate;
    }

    @Value("${activemq.queue.payment}")
    private String paymentQueue;

    @Override
    public void processPayment(BillingMessage billingMessage) {
        // This method is required by the PaymentService interface
        // In practice, it will not be called directly as the JMS listener
        // will invoke processJmsMessage instead
        log.warn("Direct processPayment call without JMS context - context propagation will not work properly");
        
        try {
            processMessageWithContext(billingMessage);
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @JmsListener(destination = "${activemq.queue.billing}")
    @Transactional
    public void processJmsMessage(Message jmsMessage) {
        try {
            // Extract the BillingMessage from the JMS Message
            BillingMessage billingMessage;
            try {
                billingMessage = (BillingMessage) messageConverter.fromMessage(jmsMessage);
            } catch (JMSException e) {
                log.error("Failed to extract BillingMessage from JMS message: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to process JMS message", e);
            }
            
            // Extract context from JMS message properties
            Context extractedContext = ContextPropagationUtil.extractContextFromJmsMessage(jmsMessage);
            
            log.info("Processing payment for transaction: {}, customer: {}", 
                    billingMessage.getTransactionId(), billingMessage.getCustomerId());
            
            // Create a child span for processing this message, using the extracted context as parent
            Span processSpan = tracer.spanBuilder("process-payment")
                    .setParent(extractedContext)
                    .setAttribute("transaction.id", billingMessage.getTransactionId().toString())
                    .setAttribute("customer.id", billingMessage.getCustomerId())
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            
            try (Scope scope = processSpan.makeCurrent()) {
                processMessageWithContext(billingMessage);
            } finally {
                processSpan.end();
            }
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let Spring JMS handle it
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

            sendToActiveMQ(paymentMessage);
            log.info("Sent payment message to ActiveMQ for payment ID: {}", paymentId);
        } finally {
            sendSpan.end();
        }
    }

    private void sendToActiveMQ(PaymentMessage message) {
        try {
            log.debug("Sending message to ActiveMQ queue: {}, payment ID: {}", 
                    paymentQueue, message.getPaymentId().toString());
            
            // Create a message post processor to inject context
            MessagePostProcessor contextInjector = ContextPropagationUtil.createContextInjector();
            
            // Get current span for logging
            Span currentSpan = Span.current();
            log.debug("Injecting trace context - TraceId: {}, SpanId: {}", 
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId());
            
            // Send the message with context headers
            jmsTemplate.convertAndSend(paymentQueue, message, contextInjector);
        } catch (Exception e) {
            log.error("Error sending message to ActiveMQ: {}", e.getMessage(), e);
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
