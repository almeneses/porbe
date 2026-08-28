package com.porbe.app.auth;

/** Token y encabezado que el cliente debe usar para proteger escrituras. */
public record CsrfResponse(String token, String headerName) {
}
