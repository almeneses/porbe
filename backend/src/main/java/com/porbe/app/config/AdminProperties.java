package com.porbe.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.admin")
/** Credenciales iniciales del administrador obtenidas desde configuración. */
public record AdminProperties(String username, String password) {
}
