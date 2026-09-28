package com.example.payment.saga;

import io.smallrye.mutiny.Uni;

public interface SagaStep {
    String getStepName();
    Uni<Void> execute(TransferContext context);
    Uni<Void> compensate(TransferContext context);
}