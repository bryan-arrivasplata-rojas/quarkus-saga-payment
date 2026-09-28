package com.example.payment.account;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class AccountResourceTest {

    @Test
    void testGetAccountsList() {
        given()
            .when().get("/accounts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    void testDebitNonExistentAccount() {
        given()
            .contentType(ContentType.JSON)
            .body(new AccountResource.AmountPayload(new BigDecimal("100.00")))
            .when().post("/accounts/999999/debit")
            .then()
            .statusCode(404)
            .body("error", containsString("Cuenta origen no existe"));
    }

    @Test
    void testDebitAndRevertCycle() {
        // Ejecutar débito en cuenta 1
        given()
            .contentType(ContentType.JSON)
            .body(new AccountResource.AmountPayload(new BigDecimal("10.00")))
            .when().post("/accounts/1/debit")
            .then()
            .statusCode(anyOf(is(200), is(400))); // 200 si hay saldo, 400 si insuficiente

        // Compensación / Reintegro
        given()
            .contentType(ContentType.JSON)
            .body(new AccountResource.AmountPayload(new BigDecimal("10.00")))
            .when().post("/accounts/1/revert-debit")
            .then()
            .statusCode(200);
    }
}