package com.gesund.demo.invoiceaggregator.service;

import com.gesund.demo.invoiceaggregator.model.Invoice;
import com.gesund.demo.invoiceaggregator.model.PaymentMessage;
import com.gesund.demo.invoiceaggregator.repository.InvoiceRepository;
import com.gesund.demo.invoiceaggregator.util.ContextPropagationUtil;
import com.gesund.demo.invoiceaggregator.util.ContextPropagatingExecutorService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "kafka")
public class KafkaInvoiceService implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ContextPropagatingExecutorService executorService;
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("kafka-invoice-processor");
    
    public KafkaInvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
        this.executorService = new ContextPropagatingExecutorService(10);
    }

    @Override
    public void processPayment(PaymentMessage message) {
        // This method is required by the InvoiceService interface
        // In practice, it will not be called directly as the Kafka listener
        // will invoke processKafkaRecord instead
        log.warn("Direct processPayment call without Kafka context - context propagation will not work properly");
        
        try {
            // Create a span for processing this message
            Span processSpan = tracer.spanBuilder("process-payment-direct")
                    .setAttribute("payment.id", message.getPaymentId().toString())
                    .setAttribute("transaction.id", message.getTransactionId().toString())
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            
            try (Scope scope = processSpan.makeCurrent()) {
                executorService.execute(() -> processMessageInThread(message));
            } finally {
                processSpan.end();
            }
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @KafkaListener(topics = "${kafka.topic.payment}", groupId = "${spring.application.name}")
    @Transactional
    public void processKafkaRecord(ConsumerRecord<String, PaymentMessage> record) {
        try {
            PaymentMessage message = record.value();
            
            // Extract context from Kafka headers
            Context extractedContext = ContextPropagationUtil.extractContextFromKafkaRecord(record);
            
            log.info("Processing payment message for transaction: {}, payment: {}", 
                    message.getTransactionId(), message.getPaymentId());
            
            // Create a child span for processing this message, using the extracted context as parent
            Span processSpan = tracer.spanBuilder("process-payment")
                    .setParent(extractedContext)
                    .setAttribute("message.id", message.getPaymentId().toString())
                    .setAttribute("transaction.id", message.getTransactionId().toString())
                    .setAttribute("kafka.topic", record.topic())
                    .setAttribute("kafka.partition", record.partition())
                    .setAttribute("kafka.offset", record.offset())
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            
            try (Scope scope = processSpan.makeCurrent()) {
                log.info("Started processing with explicit span - TraceId: {}, SpanId: {}", 
                        processSpan.getSpanContext().getTraceId(),
                        processSpan.getSpanContext().getSpanId());
                
                // Now use our context-propagating executor
                executorService.execute(() -> processMessageInThread(message));
            } finally {
                processSpan.end();
            }
        } catch (Exception e) {
            log.error("Error processing payment message: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to let Spring Kafka handle it
        }
    }

    private void processMessageInThread(PaymentMessage message) {
        // Create a span for the database operation
        Span dbSpan = tracer.spanBuilder("save-invoice")
                .setParent(Context.current())
                .setAttribute("payment.id", message.getPaymentId().toString())
                .setAttribute("transaction.id", message.getTransactionId().toString())
                .startSpan();
                
        try (Scope scope = dbSpan.makeCurrent()) {
            log.info("============ TRACE CONTEXT INFO ============");
            log.info("Processing payment in thread for payment ID: {}", message.getPaymentId());
            log.info("Current span ID: {}", Span.current().getSpanContext().getSpanId());
            log.info("Current trace ID: {}", Span.current().getSpanContext().getTraceId());
            log.info("Is sampled: {}", Span.current().getSpanContext().isSampled());
            log.info("============================================");

            // Create and save invoice
            UUID invoiceId = UUID.randomUUID();
            Invoice invoice = Invoice.builder()
                    .invoiceId(invoiceId)
                    .transactionId(message.getTransactionId())
                    .paymentId(message.getPaymentId())
                    .customerId(message.getCustomerId())
                    .productId(message.getProductId())
                    .amount(message.getAmount())
                    .currency(message.getCurrency())
                    .status("GENERATED")
                    .paymentMethod(message.getPaymentMethod())
                    .processorReference(message.getProcessorReference())
                    .createdAt(LocalDateTime.now())
                    .build();

            invoiceRepository.save(invoice);
            log.info("Saved invoice with ID: {} for payment: {}", invoiceId, message.getPaymentId());
        } catch (Exception e) {
            log.error("Error processing payment message: {}", e.getMessage(), e);
            dbSpan.recordException(e);
        } finally {
            dbSpan.end();
        }
    }
}
