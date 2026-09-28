package com.example.payment.notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Receipt(
    String receiptId,
    String sagaId,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount,
    String status,
    LocalDateTime timestamp
) {}