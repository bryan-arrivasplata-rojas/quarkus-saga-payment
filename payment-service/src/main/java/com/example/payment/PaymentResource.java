package com.example.payment.payment;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    public record AmountPayload(BigDecimal amount) {}

    @POST
    @Path("/{id}/credit")
    @Transactional
    public Response credit(@PathParam("id") Long id, AmountPayload payload) {
        PaymentAccount account = PaymentAccount.findById(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Cuenta destino no encontrada: " + id)).build();
        }
        account.balance = account.balance.add(payload.amount());
        account.updatedAt = LocalDateTime.now();
        return Response.ok(account).build();
    }

    @POST
    @Path("/{id}/revert-credit")
    @Transactional
    public Response revertCredit(@PathParam("id") Long id, AmountPayload payload) {
        PaymentAccount account = PaymentAccount.findById(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Cuenta destino no encontrada: " + id)).build();
        }
        account.balance = account.balance.subtract(payload.amount());
        account.updatedAt = LocalDateTime.now();
        return Response.ok(account).build();
    }
}