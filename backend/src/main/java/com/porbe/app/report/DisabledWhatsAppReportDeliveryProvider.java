package com.porbe.app.report;

import org.springframework.stereotype.Component;

/** Mantiene explícitamente desactivado el envío hasta configurar WhatsApp Business. */
@Component
public class DisabledWhatsAppReportDeliveryProvider implements PortfolioReportDeliveryProvider {

    @Override
    public PortfolioReportDeliveryResult deliver(PortfolioReport report) {
        return new PortfolioReportDeliveryResult(
                "NOT_CONFIGURED",
                "Informe generado. El envío por WhatsApp Business todavía no está configurado.");
    }

    @Override
    public boolean configured() {
        return false;
    }
}
