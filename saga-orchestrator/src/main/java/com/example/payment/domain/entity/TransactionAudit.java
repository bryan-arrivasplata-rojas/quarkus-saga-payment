package com.example.payment.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_audits")
public class TransactionAudit extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "saga_id", nullable = false, length = 64)
    public String sagaId;

    @Column(name = "from_account_id", nullable = false)
    public Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    public Long toAccountId;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 30)
    public String status; // DEBITED, CREDITED, REVERTED, FAILED

    @Column(name = "step_description", length = 150)
    public String stepDescription;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}