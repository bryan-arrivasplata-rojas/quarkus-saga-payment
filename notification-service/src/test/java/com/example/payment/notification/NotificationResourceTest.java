package com.example.payment.notification;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class NotificationResourceTest {

    @Test
    void testIssueReceiptSuccess() {
        String sagaId = UUID.randomUUID().toString();
        var request = new NotificationResource.IssueRequest(
            sagaId, 1L, 2L, new BigDecimal("100.00"), false
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/notifications/issue-receipt")
            .then()
            .statusCode(200)
            .body("status", equalTo("ISSUED"))
            .body("sagaId", equalTo(sagaId));
    }

    @Test
    void testIssueReceiptSimulatedFailure() {
        String sagaId = UUID.randomUUID().toString();
        var request = new NotificationResource.IssueRequest(
            sagaId, 1L, 2L, new BigDecimal("100.00"), true
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/notifications/issue-receipt")
            .then()
            .statusCode(503)
            .body("error", containsString("no disponible"));
    }

    @Test
    void testCancelReceipt() {
        given()
            .contentType(ContentType.JSON)
            .when().post("/notifications/cancel-receipt/saga-test-123")
            .then()
            .statusCode(200)
            .body("message", equalTo("Comprobante anulado"));
    }
}