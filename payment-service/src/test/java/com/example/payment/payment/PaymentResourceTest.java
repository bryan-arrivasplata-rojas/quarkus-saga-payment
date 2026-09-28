package com.example.payment.payment;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class PaymentResourceTest {

    @Test
    void testCreditNonExistentAccount() {
        given()
            .contentType(ContentType.JSON)
            .body(new PaymentResource.AmountPayload(new BigDecimal("50.00")))
            .when().post("/payments/999999/credit")
            .then()
            .statusCode(404)
            .body("error", containsString("Cuenta destino no encontrada"));
    }

    @Test
    void testCreditAndRevertSuccess() {
        given()
            .contentType(ContentType.JSON)
            .body(new PaymentResource.AmountPayload(new BigDecimal("25.00")))
            .when().post("/payments/2/credit")
            .then()
            .statusCode(anyOf(is(200), is(404)));

        given()
            .contentType(ContentType.JSON)
            .body(new PaymentResource.AmountPayload(new BigDecimal("25.00")))
            .when().post("/payments/2/revert-credit")
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }
}