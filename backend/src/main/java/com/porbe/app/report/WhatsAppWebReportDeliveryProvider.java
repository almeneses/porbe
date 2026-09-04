package com.porbe.app.report;

import com.porbe.app.config.WhatsAppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Entrega el PNG mediante el servicio interno basado en whatsapp-web.js. */
@Component
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebReportDeliveryProvider implements PortfolioReportDeliveryProvider {

    private final WhatsAppWebClient client;
    private final WhatsAppProperties properties;

    public WhatsAppWebReportDeliveryProvider(WhatsAppWebClient client, WhatsAppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PortfolioReportDeliveryResult deliver(PortfolioReport report, String recipient) {
        var target = hasText(recipient) ? recipient.trim() : properties.defaultRecipient();
        if (!hasText(target)) {
            return new PortfolioReportDeliveryResult(
                    "NOT_CONFIGURED",
                    "Configura un número predeterminado o indícalo al enviar manualmente.");
        }
        if (report.getImageData() == null || report.getImageData().length == 0) {
            throw new WhatsAppDeliveryException("El informe todavía no tiene una imagen lista para enviar.");
        }

        client.send(
                target,
                caption(report),
                filename(report),
                report.getImageData());
        return new PortfolioReportDeliveryResult("SENT", "Informe enviado por WhatsApp Web.");
    }

    @Override
    public boolean configured() {
        return hasText(properties.defaultRecipient());
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
