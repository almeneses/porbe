package com.porbe.app.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Entrega el PNG mediante el servicio interno basado en whatsapp-web.js. */
@Component
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebReportDeliveryProvider implements PortfolioReportDeliveryProvider {

    private final WhatsAppWebClient client;

    public WhatsAppWebReportDeliveryProvider(WhatsAppWebClient client) {
        this.client = client;
    }

    @Override
    public PortfolioReportDeliveryResult deliver(PortfolioReport report, String recipient) {
        if (!hasText(recipient)) {
            return new PortfolioReportDeliveryResult(
                    "NOT_CONFIGURED",
                    "Configura al menos un destinatario activo.");
        }
        if (report.getImageData() == null || report.getImageData().length == 0) {
            throw new WhatsAppDeliveryException("El informe todavía no tiene una imagen lista para enviar.");
        }

        client.send(
                recipient.trim(),
                caption(report),
                filename(report),
                report.getImageData());
        return new PortfolioReportDeliveryResult("SENT", "Informe enviado por WhatsApp Web.");
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public WhatsAppConnectionStatus connectionStatus() {
        return client.status();
    }

    @Override
    public String channel() {
        return "WHATSAPP_WEB";
    }

    private String caption(PortfolioReport report) {
        return "Informe de " + report.getPortfolioName() + " · "
                + report.getFrom() + " al " + report.getTo();
    }

    private String filename(PortfolioReport report) {
        return "informe_portafolio_" + report.getFrom() + "_" + report.getTo() + ".png";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
