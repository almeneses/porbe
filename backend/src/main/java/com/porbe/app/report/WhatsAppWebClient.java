package com.porbe.app.report;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Encapsula el contrato HTTP del servicio Node para que el resto del backend no dependa de él. */
@Component
public class WhatsAppWebClient {

    private final RestClient restClient;

    public WhatsAppWebClient(@Qualifier("whatsAppRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public WhatsAppConnectionStatus status() {
        try {
            var response = restClient.get()
                    .uri("/api/status")
                    .retrieve()
                    .body(WhatsAppConnectionStatus.class);
            return response == null ? unavailable("WhatsApp no devolvió información de la sesión.") : response;
        } catch (RuntimeException exception) {
            return unavailable("El servicio de WhatsApp no está disponible en este momento.");
        }
    }

    /** Codifica la imagen únicamente al cruzar la frontera HTTP interna. */
    public WhatsAppServiceSendResponse send(
            String recipient,
            String caption,
            String filename,
            byte[] image) {
        var request = new WhatsAppServiceReportRequest(
                recipient,
                caption,
                filename,
                Base64.getEncoder().encodeToString(image));
        try {
            var response = restClient.post()
                    .uri("/api/messages/report")
                    .body(request)
                    .retrieve()
                    .body(WhatsAppServiceSendResponse.class);
            if (response == null || !"SENT".equals(response.status())) {
                throw new WhatsAppDeliveryException("WhatsApp no confirmó el envío del informe.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new WhatsAppDeliveryException(messageFor(exception.getStatusCode().value()), exception);
        } catch (ResourceAccessException exception) {
            throw new WhatsAppDeliveryException(
                    "No fue posible comunicarse con el servicio de WhatsApp.", exception);
        }
    }

    private String messageFor(int statusCode) {
        return switch (statusCode) {
            case 409 -> "WhatsApp todavía no está conectado. Escanea el código QR e intenta nuevamente.";
            case 422 -> "Revisa el número: debe incluir el código de país y estar registrado en WhatsApp.";
            case 401 -> "El backend no pudo autenticarse con el servicio interno de WhatsApp.";
            default -> "WhatsApp no pudo enviar el informe en este momento.";
        };
    }

    private WhatsAppConnectionStatus unavailable(String message) {
        return new WhatsAppConnectionStatus("UNAVAILABLE", false, null, null, message, null);
    }
}
