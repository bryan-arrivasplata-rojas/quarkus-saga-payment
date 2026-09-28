package com.example.payment;

import com.example.payment.resource.TransferRequest;
import com.example.payment.saga.steps.CreditStep;
import com.example.payment.saga.steps.DebitStep;
import com.example.payment.saga.steps.NotificationStep;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class TransferSagaTest {

    @InjectMock
    DebitStep debitStep;

    @InjectMock
    CreditStep creditStep;

    @InjectMock
    NotificationStep notificationStep;

    @BeforeEach
    void setupMocks() {
        Mockito.reset(debitStep, creditStep, notificationStep);

        // Flujo exitoso por defecto: cada paso completa con Uni<Void> vacío
        Mockito.when(debitStep.execute(any())).thenReturn(Uni.createFrom().voidItem());
        Mockito.when(creditStep.execute(any())).thenReturn(Uni.createFrom().voidItem());
        Mockito.when(notificationStep.execute(any())).thenReturn(Uni.createFrom().voidItem());

        // Compensaciones por defecto
        Mockito.when(creditStep.compensate(any())).thenReturn(Uni.createFrom().voidItem());
        Mockito.when(debitStep.compensate(any())).thenReturn(Uni.createFrom().voidItem());
        Mockito.when(notificationStep.compensate(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void testSuccessfulTransferSaga() {
        var request = new TransferRequest(1L, 2L, new BigDecimal("75.00"), false);

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/transfers")
            .then()
            .statusCode(200)
            .body("status", equalTo("SUCCESS"));

        // Se deben haber ejecutado los 3 pasos hacia adelante
        Mockito.verify(debitStep, Mockito.times(1)).execute(any());
        Mockito.verify(creditStep, Mockito.times(1)).execute(any());
        Mockito.verify(notificationStep, Mockito.times(1)).execute(any());

        // Ninguna compensación debe haberse invocado
        Mockito.verify(creditStep, Mockito.never()).compensate(any());
        Mockito.verify(debitStep, Mockito.never()).compensate(any());
    }

    @Test
    void testCompensatedTransferWhenNotificationFails() {
        // Simulamos que el paso de notificación falla
        Mockito.when(notificationStep.execute(any()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Simulated notification service failure")));

        var request = new TransferRequest(1L, 2L, new BigDecimal("75.00"), true);

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/transfers")
            .then()
            .statusCode(409)
            .body("status", equalTo("FAILED_AND_COMPENSATED"));

        // Verificamos que se compensaron crédito y débito en orden inverso
        Mockito.verify(creditStep, Mockito.times(1)).compensate(any());
        Mockito.verify(debitStep, Mockito.times(1)).compensate(any());
    }
}