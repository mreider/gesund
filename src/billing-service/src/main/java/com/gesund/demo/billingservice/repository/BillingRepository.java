package com.gesund.demo.billingservice.repository;

import com.gesund.demo.billingservice.model.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface BillingRepository extends JpaRepository<BillingRecord, Long> {
    Optional<BillingRecord> findByTransactionId(UUID transactionId);
}
