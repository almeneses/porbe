package com.porbe.app.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nombre editable enviado al crear o renombrar un portafolio. */
public record PortfolioRequest(
        @NotBlank(message = "Escribe un nombre para el portafolio.")
        @Size(max = 120, message = "El nombre del portafolio no puede superar 120 caracteres.")
        String name) {
}
