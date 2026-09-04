package com.porbe.app.operation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Centraliza las reglas usadas tanto por Excel como por el formulario manual. */
@Component
public class OperationRules {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");
    private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z0-9^][A-Z0-9.^=\\-]{0,29}");

    private final Clock clock;

    public OperationRules(Clock clock) {
        this.clock = clock;
    }

    /** Limpia texto y traduce el tipo enviado por la UI antes de validarlo. */
    public OperationData normalize(OperationRequest request) {
        if (request == null) {
            return new OperationData(null, null, null, null, null, null, BigDecimal.ZERO, null, null);
        }
        return new OperationData(
                request.date(),
                OperationType.fromSpreadsheet(request.type()).orElse(null),
                uppercaseOrNull(request.ticker()),
                nullIfBlank(request.name()),
                request.quantity(),
                request.unitPrice(),
                request.commission() == null ? BigDecimal.ZERO : request.commission(),
                request.totalAmount(),
                nullIfBlank(request.notes()));
    }

    /** Devuelve todos los problemas para corregir el formulario en un solo intento. */
    public List<OperationFieldError> validate(OperationData data) {
        var errors = new ArrayList<OperationFieldError>();
        if (data.date() == null) {
            errors.add(error("date", "La fecha es obligatoria."));
        } else if (data.date().isAfter(LocalDate.now(clock.withZone(BUSINESS_ZONE)))) {
            errors.add(error("date", "La fecha no puede estar en el futuro."));
        }
        if (data.type() == null) {
            errors.add(error("type", "Selecciona compra, venta, dividendo, depósito o retiro."));
        }
        if (data.ticker() != null && !TICKER_PATTERN.matcher(data.ticker()).matches()) {
            errors.add(error("ticker", "El ticker no tiene un formato Yahoo Finance válido."));
        }
        if (data.name() != null && data.name().length() > 160) {
            errors.add(error("name", "El nombre no puede superar 160 caracteres."));
        }
        if (data.notes() != null && data.notes().length() > 1000) {
            errors.add(error("notes", "Las notas no pueden superar 1.000 caracteres."));
        }
        validatePositiveDecimal(data.quantity(), 8, "quantity", "La cantidad", errors);
        validatePositiveDecimal(data.unitPrice(), 8, "unitPrice", "El precio", errors);
        validateMoney(data.commission(), true, "commission", "La comisión", errors);
        validateMoney(data.totalAmount(), true, "totalAmount", "El total", errors);
        if (data.type() != null) {
            validateByType(data, errors);
        }
        return errors;
    }

    private void validateByType(OperationData data, List<OperationFieldError> errors) {
        var stockOperation = data.type() == OperationType.COMPRA
                || data.type() == OperationType.VENTA
                || data.type() == OperationType.DIVIDENDO;
        if (stockOperation && data.ticker() == null) {
            errors.add(error("ticker", "El ticker es obligatorio para esta operación."));
        }
        if (stockOperation && data.name() == null) {
            errors.add(error("name", "El nombre es obligatorio para esta operación."));
        }

        if (data.type() == OperationType.COMPRA || data.type() == OperationType.VENTA) {
            if (data.quantity() == null) {
                errors.add(error("quantity", "La cantidad es obligatoria para compras y ventas."));
            }
            if (data.unitPrice() == null) {
                errors.add(error("unitPrice", "El precio es obligatorio para compras y ventas."));
            }
            if (data.quantity() != null && data.unitPrice() != null && data.totalAmount() != null) {
                var gross = data.quantity().multiply(data.unitPrice());
                var expected = data.type() == OperationType.COMPRA
                        ? gross.add(data.commission())
                        : gross.subtract(data.commission());
                validateExpectedTotal(expected, data.totalAmount(), errors);
            }
        }

        if (data.type() == OperationType.DIVIDENDO) {
            if ((data.quantity() == null) != (data.unitPrice() == null)) {
                errors.add(error(
                        "quantity",
                        "Para dividendos, completa cantidad y precio unitario juntos o deja ambos vacíos."));
            } else if (data.quantity() != null && data.totalAmount() != null) {
                validateExpectedTotal(
                        data.quantity().multiply(data.unitPrice()).subtract(data.commission()),
                        data.totalAmount(),
                        errors);
            }
        }

        if ((data.type() == OperationType.DEPOSITO || data.type() == OperationType.RETIRO)
                && data.commission().signum() != 0) {
            errors.add(error("commission", "Depósitos y retiros deben tener comisión igual a 0."));
        }
    }

    private void validateExpectedTotal(
            BigDecimal expected,
            BigDecimal actual,
            List<OperationFieldError> errors) {
        if (expected.signum() <= 0) {
            errors.add(error("totalAmount", "El cálculo de la operación debe producir un total positivo."));
        } else if (expected.subtract(actual).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            errors.add(error("totalAmount", "El total no coincide con cantidad × precio y la comisión."));
        }
    }

    private void validatePositiveDecimal(
            BigDecimal value,
            int scale,
            String field,
            String label,
            List<OperationFieldError> errors) {
        if (value != null && (value.signum() < 0 || decimalScale(value) > scale)) {
            errors.add(error(field, label + " debe ser mayor que 0 y tener máximo " + scale + " decimales."));
        }
    }

    private void validateMoney(
            BigDecimal value,
            boolean zeroAllowed,
            String field,
            String label,
            List<OperationFieldError> errors) {
        if (value == null || (!zeroAllowed && value.signum() <= 0) || (zeroAllowed && value.signum() < 0)) {
            errors.add(error(field, label + (zeroAllowed ? " no puede ser negativa." : " debe ser mayor que 0.")));
        } else if (decimalScale(value) > 2) {
            errors.add(error(field, label + " debe tener máximo 2 decimales."));
        }
    }

    private int decimalScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private OperationFieldError error(String field, String message) {
        return new OperationFieldError(field, message);
    }

    private String uppercaseOrNull(String value) {
        var clean = nullIfBlank(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
