package com.porbe.app.importer;

/** Describe un error de validación localizado en una fila o en el archivo. */
public record ImportRowError(Integer row, String field, String message) {
}
