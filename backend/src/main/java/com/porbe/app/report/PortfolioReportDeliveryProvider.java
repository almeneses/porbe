package com.porbe.app.report;

/** Contrato para entregar informes sin acoplar el dominio a WhatsApp Business. */
public interface PortfolioReportDeliveryProvider {

    PortfolioReportDeliveryResult deliver(PortfolioReport report);

    boolean configured();
}
