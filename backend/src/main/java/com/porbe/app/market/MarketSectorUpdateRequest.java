package com.porbe.app.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sector editable usado para agrupar la composición del portafolio. */
public record MarketSectorUpdateRequest(
        @NotBlank(message = "El sector es obligatorio.")
        @Size(max = 80, message = "El sector no puede superar 80 caracteres.")
        String sector) {
}
