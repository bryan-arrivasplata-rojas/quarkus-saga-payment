package com.example.payment.saga.steps;

import com.example.payment.client.AccountClient;
import com.example.payment.saga.SagaStep;
import com.example.payment.saga.TransferContext;
import com.example.payment.service.AuditService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class DebitStep implements SagaStep {

    @Inject
    @RestClient
    AccountClient accountClient;

    @Inject
    AuditService auditService;

    @Override
    public String getStepName() {
        return "ACCOUNT_SERVICE_DEBIT";
    }

    @Override
    public Uni<Void> execute(TransferContext context) {
        return accountClient.debit(context.fromAccountId(), new AccountClient.AmountPayload(context.amount()))
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "DEBITED", "Débito reactivo ejecutado en account-service"))
            .replaceWithVoid();
    }

    @Override
    public Uni<Void> compensate(TransferContext context) {
        return accountClient.revertDebit(context.fromAccountId(), new AccountClient.AmountPayload(context.amount()))
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "REVERTED", "Compensación SAGA: Reintegro de fondos en account-service"))
            .replaceWithVoid();
    }
}