package com.porbe.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración del puente HTTP interno hacia el contenedor de WhatsApp Web. */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String baseUrl,
        String internalToken,
        String defaultRecipient,
        int connectTimeoutSeconds,
        int readTimeoutSeconds) {
}
