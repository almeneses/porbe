package com.porbe.app.operation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** Tipos de movimiento aceptados por la plantilla de portafolio. */
public enum OperationType {
    COMPRA("compra", -1),
    VENTA("venta", 1),
    DIVIDENDO("dividendo", 1),
    DEPOSITO("depósito", 1),
    RETIRO("retiro", -1);

    private final String label;
    private final int cashSign;

    OperationType(String label, int cashSign) {
        this.label = label;
        this.cashSign = cashSign;
    }

    public String label() {
        return label;
    }

    public int cashSign() {
        return cashSign;
    }

    public static Optional<OperationType> fromSpreadsheet(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return switch (normalized) {
            case "compra" -> Optional.of(COMPRA);
            case "venta" -> Optional.of(VENTA);
            case "dividendo" -> Optional.of(DIVIDENDO);
            case "deposito" -> Optional.of(DEPOSITO);
            case "retiro" -> Optional.of(RETIRO);
            default -> Optional.empty();
        };
    }
}
