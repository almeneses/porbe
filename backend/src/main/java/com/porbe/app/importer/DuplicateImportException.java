package com.porbe.app.importer;

/** Señala que un archivo ya fue procesado y no debe duplicar operaciones. */
public class DuplicateImportException extends RuntimeException {

    public DuplicateImportException() {
        super("Este archivo ya fue importado anteriormente.");
    }
}
