package com.porbe.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.market-data.yahoo")
/** Parámetros configurables para consultar y sincronizar datos de mercado. */
public record MarketDataProperties(
        String baseUrl,
        String userAgent,
        int connectTimeoutSeconds,
        int readTimeoutSeconds) {
}
