package com.porbe.app.report;

import com.porbe.app.config.WhatsAppProperties;
import org.springframework.stereotype.Component;

/** Entrega informes mediante el servicio interno de WhatsApp Web. */
@Component
public class PortfolioReportDeliveryProvider {

    private static final String DISABLED_MESSAGE = "El servicio de WhatsApp Web está desactivado.";

    private final WhatsAppWebClient client;
    private final WhatsAppProperties properties;

    public PortfolioReportDeliveryProvider(WhatsAppWebClient client, WhatsAppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public PortfolioReportDeliveryResult deliver(PortfolioReport report, String recipient) {
        if (!configured()) {
            return new PortfolioReportDeliveryResult("NOT_CONFIGURED", DISABLED_MESSAGE);
        }
        if (recipient == null || recipient.isBlank()) {
            return new PortfolioReportDeliveryResult(
                    "NOT_CONFIGURED",
                    "Configura al menos un destinatario activo.");
        }
        if (report.getImageData() == null || report.getImageData().length == 0) {
            throw new WhatsAppDeliveryException("El informe todavía no tiene una imagen lista para enviar.");
        }

        client.send(recipient.trim(), caption(report), filename(report), report.getImageData());
        return new PortfolioReportDeliveryResult("SENT", "Informe enviado por WhatsApp Web.");
    }

    public boolean configured() {
        return properties.enabled();
    }

    public WhatsAppConnectionStatus connectionStatus() {
        return configured()
                ? client.status()
                : new WhatsAppConnectionStatus("DISABLED", false, null, null, DISABLED_MESSAGE, null);
    }

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
}
