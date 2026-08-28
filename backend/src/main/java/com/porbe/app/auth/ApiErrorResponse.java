package com.porbe.app.auth;

/** Contrato uniforme para comunicar errores consumibles por el frontend. */
public record ApiErrorResponse(String code, String message) {
}
