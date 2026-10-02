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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class PortfolioReportAiNoteServiceTest {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @CsvSource(value = {"0.09, 9%", "NULL, no disponible"}, nullValues = "NULL")
    void createsStructuredNoteWithAtMostTwoActions(BigDecimal totalReturn, String expectedReturn) throws Exception {
        var executable = temporaryDirectory.resolve("codex");
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "debug" ]; then
                  printf '%s' '{"models":[{"slug":"gpt-5.5","display_name":"GPT-5.5","default_reasoning_level":"medium","supported_reasoning_levels":[{"effort":"low"},{"effort":"medium"}],"visibility":"list"},{"slug":"hidden","display_name":"Hidden","default_reasoning_level":"high","supported_reasoning_levels":[],"visibility":"hide"}]}'
                  exit 0
                fi
                printf '%s\n' "$@" > "$(dirname "$0")/arguments.txt"
                cat > "$(dirname "$0")/prompt.txt"
                printf '%s' '{"title":"Un periodo positivo","body":"El portafolio creció con apoyo de Ecopetrol.","actions":["Mantener la diversificación.","Revisar la concentración."]}'
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        var service = new PortfolioReportAiNoteService(new ObjectMapper(), executable.toString(), "read-only", 5);

        var info = service.info(new PortfolioReportAiSettings(true, "gpt-5.5", "medium"));

        var note = service.create(data(totalReturn), new PortfolioReportAiSettings(true, "test-model", "low"));

        assertThat(info.catalogAvailable()).isTrue();
        assertThat(info.models()).singleElement().satisfies(model -> {
            assertThat(model.model()).isEqualTo("gpt-5.5");
            assertThat(model.efforts()).containsExactly("low", "medium");
        });
        assertThat(note.title()).isEqualTo("Un periodo positivo");
        assertThat(note.body()).contains("Ecopetrol");
        assertThat(note.actions()).containsExactly("Mantener la diversificación.", "Revisar la concentración.");
        assertThat(Files.readString(temporaryDirectory.resolve("arguments.txt")))
                .contains("exec", "--ephemeral", "test-model", "model_reasoning_effort=\"low\"", "read-only", "--output-schema", "-");
        assertThat(Files.readString(temporaryDirectory.resolve("prompt.txt")))
                .contains("Ecopetrol", "Resultado del periodo: 200 (10%)")
                .contains("Cierres comparados para el rendimiento: 2026-01-02 a 2026-01-16")
                .contains("Rentabilidad total: " + expectedReturn);
    }

    @Test
    void refreshesCachedModelsAndKeepsTheCacheWhenRefreshFails() throws Exception {
        var executable = temporaryDirectory.resolve("codex");
        var catalog = temporaryDirectory.resolve("catalog.json");
        Files.writeString(executable, """
                #!/bin/sh
                cat "$(dirname "$0")/catalog.json"
                """);
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"));
        var service = new PortfolioReportAiNoteService(new ObjectMapper(), executable.toString(), "read-only", 5);
        var settings = new PortfolioReportAiSettings(true, "saved-model", "low");
        var original = """
                {"models":[{"slug":"old-model","display_name":"Old model","default_reasoning_level":"low","supported_reasoning_levels":[{"effort":"low"}],"visibility":"list"}]}
                """;
        Files.writeString(catalog, original);
        assertThat(service.info(settings).models()).extracting(PortfolioReportAiModelOption::model)
                .containsExactly("saved-model", "old-model");

        Files.writeString(catalog, original.replace("old-model", "new-model"));
        assertThat(service.info(settings).models()).extracting(PortfolioReportAiModelOption::model)
                .containsExactly("saved-model", "old-model");
        var refreshed = service.info(settings, true);
        assertThat(refreshed.catalogAvailable()).isTrue();
        assertThat(refreshed.model()).isEqualTo(settings.model());
        assertThat(refreshed.effort()).isEqualTo(settings.effort());
        assertThat(refreshed.models()).extracting(PortfolioReportAiModelOption::model)
                .containsExactly("saved-model", "new-model");

        Files.writeString(catalog, "{\"models\":[]}");
        assertThat(service.info(settings, true).catalogAvailable()).isFalse();
        assertThat(service.info(settings).models()).isEqualTo(refreshed.models());
        Files.delete(catalog);
        assertThat(service.info(settings, true).catalogAvailable()).isFalse();
        assertThat(service.info(settings).models()).isEqualTo(refreshed.models());
    }

    private PortfolioReportData data(BigDecimal totalReturn) {
        return new PortfolioReportData(
                1L, "Portafolio de prueba", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 16), "COP",
                new BigDecimal("200"), new BigDecimal("0.10"),
                new PortfolioReportAssetHighlight(
                        "ECOPETROL.CL", "Ecopetrol", new BigDecimal("0.10"), new BigDecimal("180"), null),
                null, new BigDecimal("20"), new BigDecimal("220"), new BigDecimal("0.11"),
                totalReturn, new BigDecimal("2000"), new BigDecimal("1000"), new BigDecimal("2200"),
                0, List.of(), List.of(), List.of(), List.of(),
                List.of(new PortfolioReportAllocation(
                        "ECOPETROL.CL", "Ecopetrol", new BigDecimal("0.75"), null)),
                List.of(), true, 0, 0);
    }
}
