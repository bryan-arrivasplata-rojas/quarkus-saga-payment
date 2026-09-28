package com.example.payment.saga;

import java.math.BigDecimal;

public record TransferContext(
    String sagaId,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount,
    boolean simulateNotificationFailure
) {}