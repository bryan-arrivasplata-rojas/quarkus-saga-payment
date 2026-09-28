package com.example.payment.saga;

import com.example.payment.domain.entity.SagaTransaction;
import com.example.payment.saga.steps.CreditStep;
import com.example.payment.saga.steps.DebitStep;
import com.example.payment.saga.steps.NotificationStep;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class TransferSagaOrchestrator {

    @Inject
    DebitStep debitStep;

    @Inject
    CreditStep creditStep;

    @Inject
    NotificationStep notificationStep;

    private final BroadcastProcessor<String> eventStream = BroadcastProcessor.create();

    public Multi<String> getEventStream() {
        return eventStream;
    }

    public Uni<Boolean> executeReactive(TransferContext context) {
        // Ejecutamos la inserción inicial en el Worker Pool directamente
        return Uni.createFrom().item(() -> {
                initSagaRecord(context.sagaId());
                emitEvent("SAGA_INIT: " + context.sagaId());
                return true;
            })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            // 1. Débito
            .chain(() -> {
                emitEvent("EXECUTING_DEBIT: Cuenta " + context.fromAccountId());
                return debitStep.execute(context);
            })
            // 2. Crédito
            .chain(() -> {
                emitEvent("EXECUTING_CREDIT: Cuenta " + context.toAccountId());
                return creditStep.execute(context)
                    .onFailure().recoverWithUni(err -> {
                        emitEvent("CREDIT_FAILED: " + err.getMessage() + " -> Iniciando Rollback");
                        return debitStep.compensate(context)
                            .chain(() -> Uni.createFrom().failure(err));
                    });
            })
            // 3. Notificación
            .chain(() -> {
                emitEvent("EXECUTING_NOTIFICATION: Emitiendo comprobante");
                return notificationStep.execute(context)
                    .onFailure().recoverWithUni(err -> {
                        emitEvent("NOTIFICATION_FAILED: " + err.getMessage() + " -> Iniciando Rollback Multi-servicio");
                        return creditStep.compensate(context)
                            .chain(() -> debitStep.compensate(context))
                            .chain(() -> Uni.createFrom().failure(err));
                    });
            })
            // Éxito global
            .chain(() -> Uni.createFrom().item(() -> {
                markSagaCompleted(context.sagaId());
                emitEvent("SAGA_COMPLETED: " + context.sagaId());
                return true;
            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
            // Compensado global
            .onFailure().recoverWithUni(err -> Uni.createFrom().item(() -> {
                markSagaFailed(context.sagaId(), err.getMessage());
                emitEvent("SAGA_COMPENSATED: " + context.sagaId());
                return false;
            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
    }

    private void emitEvent(String message) {
        eventStream.onNext("[" + LocalDateTime.now() + "] " + message);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void initSagaRecord(String sagaId) {
        SagaTransaction tx = new SagaTransaction();
        tx.sagaId = sagaId;
        tx.status = "PENDING";
        tx.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markSagaCompleted(String sagaId) {
        SagaTransaction tx = SagaTransaction.find("sagaId", sagaId).firstResult();
        if (tx != null) {
            tx.status = "COMPLETED";
            tx.updatedAt = LocalDateTime.now();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markSagaFailed(String sagaId, String error) {
        SagaTransaction tx = SagaTransaction.find("sagaId", sagaId).firstResult();
        if (tx != null) {
            tx.status = "COMPENSATED";
            tx.errorMessage = error;
            tx.updatedAt = LocalDateTime.now();
        }
    }
}