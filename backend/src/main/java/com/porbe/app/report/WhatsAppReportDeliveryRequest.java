package com.porbe.app.report;

import jakarta.validation.constraints.NotNull;

/** Destinatario guardado elegido para el envío manual. */
public record WhatsAppReportDeliveryRequest(
        @NotNull(message = "Selecciona un destinatario de WhatsApp.")
        Long recipientId) {
}
