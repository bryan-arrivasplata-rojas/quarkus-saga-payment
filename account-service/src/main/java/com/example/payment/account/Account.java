package com.example.payment.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "owner_name", nullable = false, length = 100)
    public String ownerName;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal balance;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt = LocalDateTime.now();
}