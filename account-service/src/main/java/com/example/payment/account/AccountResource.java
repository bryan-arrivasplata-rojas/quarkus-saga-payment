package com.example.payment.account;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    public record AmountPayload(BigDecimal amount) {}

    @GET
    public List<Account> listAll() {
        return Account.listAll();
    }

    @GET
    @Path("/{id}")
    public Response getAccount(@PathParam("id") Long id) {
        Account account = Account.findById(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Cuenta no encontrada")).build();
        }
        return Response.ok(account).build();
    }

    @POST
    @Path("/{id}/debit")
    @Transactional
    public Response debit(@PathParam("id") Long id, AmountPayload payload) {
        Account account = Account.findById(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Cuenta origen no existe: " + id)).build();
        }
        if (account.balance.compareTo(payload.amount()) < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Saldo insuficiente")).build();
        }
        account.balance = account.balance.subtract(payload.amount());
        account.updatedAt = LocalDateTime.now();
        return Response.ok(account).build();
    }

    @POST
    @Path("/{id}/revert-debit")
    @Transactional
    public Response revertDebit(@PathParam("id") Long id, AmountPayload payload) {
        Account account = Account.findById(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Cuenta no existe")).build();
        }
        account.balance = account.balance.add(payload.amount());
        account.updatedAt = LocalDateTime.now();
        return Response.ok(account).build();
    }
}