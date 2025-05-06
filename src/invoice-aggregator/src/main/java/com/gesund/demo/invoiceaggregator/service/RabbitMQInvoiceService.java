package com.gesund.demo.invoiceaggregator.service;

import com.gesund.demo.invoiceaggregator.model.Invoice;
import com.gesund.demo.invoiceaggregator.model.PaymentMessage;
import com.gesund.demo.invoiceaggregator.repository.InvoiceRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import com.gesund.demo.invoiceaggregator.util.ContextPropagatingExecutorService;

@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "rabbitmq")
public class RabbitMQInvoiceService implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ContextPropagatingExecutorService executorService;
    
    public RabbitMQInvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
        this.executorService = new ContextPropagatingExecutorService(10);
    }

    @Override
    @RabbitListener(queues = "${rabbitmq.queue.payment}")
    @Transactional
    public void processPayment(PaymentMessage message) {
        log.info("Processing payment message for transaction: {}, payment: {}", 
                message.getTransactionId(), message.getPaymentId());

        // Create a child span for processing
        Span currentSpan = Span.current();
        log.info("Processing message with trace ID: {}, span ID: {}", 
                currentSpan.getSpanContext().getTraceId(),
                currentSpan.getSpanContext().getSpanId());
        
        // Use our context-propagating executor to ensure trace context is maintained
        executorService.execute(() -> processMessageInThread(message));
    }

    private void processMessageInThread(PaymentMessage message) {
        try {
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
        }
    }
}
