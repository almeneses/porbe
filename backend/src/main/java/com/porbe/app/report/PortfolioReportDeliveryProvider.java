package com.porbe.app.report;

/** Contrato para entregar informes sin acoplar el dominio a una implementación de WhatsApp. */
public interface PortfolioReportDeliveryProvider {

    PortfolioReportDeliveryResult deliver(PortfolioReport report, String recipient);

    boolean configured();

    WhatsAppConnectionStatus connectionStatus();

    String channel();
}
