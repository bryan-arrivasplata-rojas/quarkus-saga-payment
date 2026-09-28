package com.example.payment.resource;

import com.example.payment.client.AccountClient;
import com.example.payment.domain.entity.SagaTransaction;
import com.example.payment.domain.entity.TransactionAudit;
import com.example.payment.saga.TransferContext;
import com.example.payment.saga.TransferSagaOrchestrator;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment Saga API", description = "Orquestador SAGA reactivo con Mutiny (Uni y Multi)")
public class TransferResource {

    @Inject
    TransferSagaOrchestrator orchestrator;

    @Inject
    @RestClient
    AccountClient accountClient;

    @POST
    @Path("/transfers")
    @Operation(summary = "Ejecutar transferencia monetaria distribuida con SAGA (Reactivo con Uni)")
    public Uni<Response> executeTransfer(TransferRequest request) {
        String sagaId = UUID.randomUUID().toString();
        TransferContext context = new TransferContext(
            sagaId,
            request.fromAccountId(),
            request.toAccountId(),
            request.amount(),
            request.simulateNotificationFailure()
        );

        return orchestrator.executeReactive(context)
            .map(success -> {
                if (success) {
                    return Response.ok(Map.of(
                        "status", "SUCCESS",
                        "sagaId", sagaId,
                        "message", "Transferencia y comprobante completados exitosamente en los 3 microservicios (Reactivo Uni)"
                    )).build();
                } else {
                    return Response.status(Response.Status.CONFLICT).entity(Map.of(
                        "status", "FAILED_AND_COMPENSATED",
                        "sagaId", sagaId,
                        "message", "Fallo reactivo. La compensación SAGA restauró saldos y comprobantes vía HTTP (Reactivo Uni)."
                    )).build();
                }
            });
    }

    @GET
    @Path("/transfers/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(summary = "Stream SSE de eventos SAGA en tiempo real (Reactivo con Multi)")
    public Multi<String> streamSagaEvents() {
        return orchestrator.getEventStream();
    }

    @GET
    @Path("/accounts")
    @Operation(summary = "Consultar cuentas mediante HTTP reactivo (Uni)")
    public Uni<List<Map<String, Object>>> getAccounts() {
        return accountClient.listAll();
    }

    @GET
    @Path("/audits")
    @Operation(summary = "Consultar trazabilidad de auditoría de la SAGA")
    @SuppressWarnings("unchecked")
    public Uni<List<TransactionAudit>> getAudits() {
        return Uni.createFrom().item(() -> (List<TransactionAudit>) (List<?>) TransactionAudit.listAll())
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @GET
    @Path("/sagas")
    @Operation(summary = "Consultar historial y estado de las Sagas")
    @SuppressWarnings("unchecked")
    public Uni<List<SagaTransaction>> getSagas() {
        return Uni.createFrom().item(() -> (List<SagaTransaction>) (List<?>) SagaTransaction.listAll())
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}