package com.porbe.app.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Número elegido temporalmente por el usuario para probar un envío manual. */
public record WhatsAppReportDeliveryRequest(
        @NotBlank(message = "Indica el número de WhatsApp con código de país.")
        @Size(max = 30, message = "El número de WhatsApp es demasiado largo.")
        String recipient) {
}
