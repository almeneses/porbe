package com.porbe.app.report;

/** Cuerpo privado que transporta el PNG desde Java hasta el contenedor Node. */
record WhatsAppServiceReportRequest(
        String to,
        String caption,
        String filename,
        String mediaBase64) {
}
