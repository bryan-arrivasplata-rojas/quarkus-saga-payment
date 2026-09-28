package com.example.payment.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.math.BigDecimal;
import java.util.Map;

@RegisterRestClient(configKey = "notification-api")
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface NotificationClient {

    record IssueRequest(String sagaId, Long fromAccountId, Long toAccountId, BigDecimal amount, boolean simulateFailure) {}

    @POST
    @Path("/issue-receipt")
    Uni<Map<String, Object>> issueReceipt(IssueRequest request);

    @POST
    @Path("/cancel-receipt/{sagaId}")
    Uni<Map<String, Object>> cancelReceipt(@PathParam("sagaId") String sagaId);
}