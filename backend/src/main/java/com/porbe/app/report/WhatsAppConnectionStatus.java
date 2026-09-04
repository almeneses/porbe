package com.porbe.app.report;

import java.time.OffsetDateTime;

/** Estado seguro de la sesión de WhatsApp que puede mostrarse en la UI autenticada. */
public record WhatsAppConnectionStatus(
        String state,
        boolean ready,
        String qrDataUrl,
        String accountLabel,
        String message,
        OffsetDateTime updatedAt) {
}
