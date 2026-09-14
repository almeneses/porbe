package com.porbe.app.report;

import java.time.OffsetDateTime;

public record WhatsAppRecipientResponse(
        Long id,
        String name,
        String phoneNumber,
        boolean enabled,
        String updatedBy,
        OffsetDateTime updatedAt) {
}
