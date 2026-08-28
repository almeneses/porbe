package com.porbe.app.auth;

import jakarta.validation.constraints.NotBlank;

/** Credenciales recibidas por el endpoint de inicio de sesión. */
public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio.") String username,
        @NotBlank(message = "La contraseña es obligatoria.") String password) {
}
