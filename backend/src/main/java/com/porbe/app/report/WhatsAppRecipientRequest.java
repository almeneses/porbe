package com.porbe.app.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WhatsAppRecipientRequest(
        @NotBlank(message = "Indica un nombre para el destinatario.")
        @Size(max = 80, message = "El nombre del destinatario es demasiado largo.")
        String name,
        @NotBlank(message = "Indica el número de WhatsApp con código de país.")
        @Size(max = 30, message = "El número de WhatsApp es demasiado largo.")
        String phoneNumber,
        boolean enabled) {
}
