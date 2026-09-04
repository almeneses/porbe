package com.porbe.app.report;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Mantiene una respuesta explícita cuando el servicio de WhatsApp Web está desactivado. */
@Component
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledWhatsAppReportDeliveryProvider implements PortfolioReportDeliveryProvider {

    @Override
    public PortfolioReportDeliveryResult deliver(PortfolioReport report, String recipient) {
        return new PortfolioReportDeliveryResult(
                "NOT_CONFIGURED",
                "El servicio de WhatsApp Web está desactivado.");
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public WhatsAppConnectionStatus connectionStatus() {
        return new WhatsAppConnectionStatus(
                "DISABLED", false, null, null, "El servicio de WhatsApp Web está desactivado.", null);
    }

    @Override
    public String channel() {
        return "WHATSAPP_WEB";
    }
}
