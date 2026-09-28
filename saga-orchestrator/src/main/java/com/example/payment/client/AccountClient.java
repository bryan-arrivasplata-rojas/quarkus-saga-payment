package com.example.payment.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "account-api")
@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AccountClient {

    record AmountPayload(BigDecimal amount) {}

    @GET
    Uni<List<Map<String, Object>>> listAll();

    @POST
    @Path("/{id}/debit")
    Uni<Map<String, Object>> debit(@PathParam("id") Long id, AmountPayload payload);

    @POST
    @Path("/{id}/revert-debit")
    Uni<Map<String, Object>> revertDebit(@PathParam("id") Long id, AmountPayload payload);
}