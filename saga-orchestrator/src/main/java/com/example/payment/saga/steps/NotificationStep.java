package com.example.payment.saga.steps;

import com.example.payment.client.NotificationClient;
import com.example.payment.saga.SagaStep;
import com.example.payment.saga.TransferContext;
import com.example.payment.service.AuditService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class NotificationStep implements SagaStep {

    @Inject
    @RestClient
    NotificationClient notificationClient;

    @Inject
    AuditService auditService;

    @Override
    public String getStepName() {
        return "NOTIFICATION_SERVICE_RECEIPT";
    }

    @Override
    public Uni<Void> execute(TransferContext context) {
        return notificationClient.issueReceipt(new NotificationClient.IssueRequest(
                context.sagaId(),
                context.fromAccountId(),
                context.toAccountId(),
                context.amount(),
                context.simulateNotificationFailure()
            ))
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "RECEIPT_ISSUED", "Comprobante emitido en notification-service"))
            .replaceWithVoid();
    }

    @Override
    public Uni<Void> compensate(TransferContext context) {
        return notificationClient.cancelReceipt(context.sagaId())
            .emitOn(Infrastructure.getDefaultWorkerPool())
            .invoke(() -> auditService.log(context, "REVERTED", "Compensación SAGA: Comprobante anulado"))
            .replaceWithVoid();
    }
}