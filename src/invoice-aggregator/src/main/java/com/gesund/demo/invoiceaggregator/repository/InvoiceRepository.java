package com.gesund.demo.invoiceaggregator.repository;

import com.gesund.demo.invoiceaggregator.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceId(UUID invoiceId);
    Optional<Invoice> findByTransactionId(UUID transactionId);
    Optional<Invoice> findByPaymentId(UUID paymentId);
}
