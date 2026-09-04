package com.porbe.app.report;

/** Error controlado al consultar la sesión o enviar una imagen por WhatsApp Web. */
public class WhatsAppDeliveryException extends RuntimeException {

    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
