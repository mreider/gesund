package com.gesund.demo.paymentprocessor.repository;

import com.gesund.demo.paymentprocessor.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(UUID transactionId);
    Optional<Payment> findByPaymentId(UUID paymentId);
}
