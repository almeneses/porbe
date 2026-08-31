package com.porbe.app.market;

import java.util.Locale;
import java.util.Map;

/** Sugiere sectores iniciales para símbolos frecuentes de la BVC. */
public final class MarketSectorCatalog {

    public static final String UNCLASSIFIED = "Sin clasificar";

    private static final Map<String, String> KNOWN_SECTORS = Map.ofEntries(
            Map.entry("ECOPETROL.CL", "Energía"),
            Map.entry("PBRCO.CL", "Energía"),
            Map.entry("TERPEL.CL", "Energía"),
            Map.entry("CELSIA.CL", "Servicios públicos"),
            Map.entry("GEB.CL", "Servicios públicos"),
            Map.entry("ISA.CL", "Servicios públicos"),
            Map.entry("CEMARGOS.CL", "Materiales"),
            Map.entry("MINEROS.CL", "Materiales"),
            Map.entry("GRUPOARGOS.CL", "Industria"),
            Map.entry("PFGRUPOARG.CL", "Industria"),
            Map.entry("BCOLOMBIA.CL", "Servicios financieros"),
            Map.entry("PFBCOLOM.CL", "Servicios financieros"),
            Map.entry("BOGOTA.CL", "Servicios financieros"),
            Map.entry("BVC.CL", "Servicios financieros"),
            Map.entry("PFBVC.CL", "Servicios financieros"),
            Map.entry("GRUPOSURA.CL", "Servicios financieros"),
            Map.entry("PFGRUPSURA.CL", "Servicios financieros"),
            Map.entry("PFDAVIGRP.CL", "Servicios financieros"),
            Map.entry("PFCIBEST.CL", "Servicios financieros"),
            Map.entry("NUTRESA.CL", "Consumo"),
            Map.entry("ÉXITO.CL", "Consumo"),
            Map.entry("EXITO.CL", "Consumo"));

    private MarketSectorCatalog() {
    }

    public static String suggestedSector(String ticker) {
        if (ticker == null) {
            return UNCLASSIFIED;
        }
        return KNOWN_SECTORS.getOrDefault(ticker.trim().toUpperCase(Locale.ROOT), UNCLASSIFIED);
    }
}
