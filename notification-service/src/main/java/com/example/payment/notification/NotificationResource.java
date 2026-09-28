package com.example.payment.notification;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {

    public record IssueRequest(String sagaId, Long fromAccountId, Long toAccountId, BigDecimal amount, boolean simulateFailure) {}

    private static final Map<String, Receipt> receipts = new ConcurrentHashMap<>();

    @POST
    @Path("/issue-receipt")
    public Response issueReceipt(IssueRequest request) {
        // Simulación controlada de fallo para probar la compensación multisericio
        if (request.simulateFailure()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Servicio de notificaciones/comprobantes no disponible")).build();
        }

        String receiptId = "REC-" + UUID.randomUUID().toString().substring(0, 8);
        Receipt receipt = new Receipt(
            receiptId,
            request.sagaId(),
            request.fromAccountId(),
            request.toAccountId(),
            request.amount(),
            "ISSUED",
            LocalDateTime.now()
        );
        receipts.put(request.sagaId(), receipt);
        return Response.ok(receipt).build();
    }

    @POST
    @Path("/cancel-receipt/{sagaId}")
    public Response cancelReceipt(@PathParam("sagaId") String sagaId) {
        Receipt receipt = receipts.remove(sagaId);
        return Response.ok(Map.of(
            "message", "Comprobante anulado",
            "sagaId", sagaId
        )).build();
    }
}