package com.example.payment.saga.steps;

import com.example.payment.client.PaymentClient;
import com.example.payment.saga.SagaStep;
import com.example.payment.saga.TransferContext;
import com.example.payment.service.AuditService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class CreditStep implements SagaStep {

    @Inject
    @RestClient
    PaymentClient paymentClient;

    @Inject
    AuditService auditService;

    @Override
    public String getStepName() {
        return "PAYMENT_SERVICE_CREDIT";
    }

    @Override
    public Uni<Void> execute(TransferContext context) {
        return paymentClient.credit(context.toAccountId(), new PaymentClient.AmountPayload(context.amount()))
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "CREDITED", "Crédito reactivo ejecutado en payment-service"))
            .replaceWithVoid();
    }

    @Override
    public Uni<Void> compensate(TransferContext context) {
        return paymentClient.revertCredit(context.toAccountId(), new PaymentClient.AmountPayload(context.amount()))
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "REVERTED", "Compensación SAGA: Fondos descontados en payment-service"))
            .replaceWithVoid();
    }
}