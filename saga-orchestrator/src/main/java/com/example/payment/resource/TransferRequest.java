package com.example.payment.resource;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.math.BigDecimal;

public record TransferRequest(
    @Schema(example = "1", description = "ID de la cuenta emisora en account-service") Long fromAccountId,
    @Schema(example = "2", description = "ID de la cuenta receptora en payment-service") Long toAccountId,
    @Schema(example = "100.00", description = "Monto a transferir") BigDecimal amount,
    @Schema(example = "false", description = "Si es true, fuerza la caída del servicio de notificaciones para probar el rollback completo") boolean simulateNotificationFailure
) {}