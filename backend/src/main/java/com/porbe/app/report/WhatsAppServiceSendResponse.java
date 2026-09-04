package com.porbe.app.report;

import java.time.OffsetDateTime;

/** Confirmación mínima devuelta por el servicio Node después de un envío. */
record WhatsAppServiceSendResponse(
        String status,
        String messageId,
        OffsetDateTime sentAt) {
}
