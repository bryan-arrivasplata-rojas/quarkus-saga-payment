package com.example.payment.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_transactions")
public class SagaTransaction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "saga_id", unique = true, nullable = false, length = 64)
    public String sagaId;

    @Column(nullable = false, length = 30)
    public String status; // PENDING, COMPLETED, FAILED, COMPENSATED

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    public LocalDateTime updatedAt = LocalDateTime.now();
}