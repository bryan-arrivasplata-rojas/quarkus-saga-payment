package com.example.payment.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.math.BigDecimal;
import java.util.Map;

@RegisterRestClient(configKey = "payment-api")
@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PaymentClient {

    record AmountPayload(BigDecimal amount) {}

    @POST
    @Path("/{id}/credit")
    Uni<Map<String, Object>> credit(@PathParam("id") Long id, AmountPayload payload);

    @POST
    @Path("/{id}/revert-credit")
    Uni<Map<String, Object>> revertCredit(@PathParam("id") Long id, AmountPayload payload);
}