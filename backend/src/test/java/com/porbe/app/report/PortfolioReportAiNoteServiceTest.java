package com.porbe.app.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class PortfolioReportAiNoteServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsStructuredNoteWithAtMostTwoActions() throws Exception {
        var executable = temporaryDirectory.resolve("codex");
        Files.writeString(executable, """
                #!/bin/sh
                printf '%s\n' "$@" > "$(dirname "$0")/arguments.txt"
                cat > "$(dirname "$0")/prompt.txt"
                printf '%s' '{"title":"Un periodo positivo","body":"El portafolio creció con apoyo de Ecopetrol.","actions":["Mantener la diversificación.","Revisar la concentración."]}'
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        var service = new PortfolioReportAiNoteService(new ObjectMapper(), executable.toString(), "read-only", 5);

        var note = service.create(data());

        assertThat(note.title()).isEqualTo("Un periodo positivo");
        assertThat(note.body()).contains("Ecopetrol");
        assertThat(note.actions()).containsExactly("Mantener la diversificación.", "Revisar la concentración.");
        assertThat(Files.readString(temporaryDirectory.resolve("arguments.txt")))
                .contains("exec", "--ephemeral", "read-only", "--output-schema", "-");
        assertThat(Files.readString(temporaryDirectory.resolve("prompt.txt")))
                .contains("No uses herramientas", "Ecopetrol", "Resultado del periodo: 200 (10%)");
    }

    private PortfolioReportData data() {
        return new PortfolioReportData(
                1L, "Portafolio de prueba", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 16), "COP",
                new BigDecimal("200"), new BigDecimal("0.10"),
                new PortfolioReportAssetHighlight(
                        "ECOPETROL.CL", "Ecopetrol", new BigDecimal("0.10"), new BigDecimal("180"), null),
                null, new BigDecimal("20"), new BigDecimal("220"), new BigDecimal("0.11"),
                new BigDecimal("0.09"), new BigDecimal("2000"), new BigDecimal("1000"), new BigDecimal("2200"),
                0, List.of(), List.of(), List.of(), List.of(),
                List.of(new PortfolioReportAllocation(
                        "ECOPETROL.CL", "Ecopetrol", new BigDecimal("0.75"), null)),
                List.of(), true, 0, 0);
    }
}
