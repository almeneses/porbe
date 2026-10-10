package com.porbe.app.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.portfolio.PortfolioHistoryResponse;
import com.porbe.app.portfolio.PortfolioHistoryService;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class PortfolioReportAiGuidanceTest {
    @Autowired PortfolioReportAiGuidanceService guidance;
    @Autowired PortfolioReportAiGuidanceRepository guidanceRepository;
    @Autowired PortfolioReportService reports;
    @MockitoSpyBean PortfolioReportRepository reportRepository;
    @Autowired PortfolioService portfolios;
    @Autowired MockMvc mvc;
    @MockitoBean PortfolioReportCalculator calculator;
    @MockitoBean PortfolioReportAiNoteService ai;
    @MockitoBean PortfolioReportArtifactRenderer renderer;
    @MockitoBean PortfolioHistoryService history;
    @MockitoBean Clock clock;
    private Long portfolioId;
    private PortfolioReportData data;
    private final PortfolioReportTemplateModel.Note note = new PortfolioReportTemplateModel.Note(
            "Título", "Comentario", List.of(), List.of());

    @BeforeEach
    void setup() {
        reset(ai, calculator, renderer, history, clock, reportRepository);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T20:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        guidanceRepository.deleteAll();
        reportRepository.deleteAll();
        portfolioId = portfolios.create("IA " + System.nanoTime()).id();
        data = new PortfolioReportData(portfolioId, "Prueba", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 9), "COP", BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 0, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), true, 0, 0);
        when(calculator.calculate(eq(portfolioId), any(), any())).thenReturn(data);
        // No actual CLI, browser or delivery is invoked by these lifecycle tests.
        when(history.weeklyHistory(eq(portfolioId), any(), any())).thenReturn(
                org.mockito.Mockito.mock(PortfolioHistoryResponse.class));
        when(renderer.render(any(), any())).thenReturn(new PortfolioReportArtifacts(new byte[]{1}, new byte[]{2}));
        when(ai.create(any(), any(), anyString(), any())).thenReturn(note);
        when(ai.create(any(), any())).thenReturn(note);
    }

    private PortfolioReportListItem generate() {
        return reports.generate(portfolioId, data.from(), data.to(), "MANUAL", "admin");
    }

    @Test
    void keepsOnlyLastExplicitSaveAndUsesItOnceForItsPortfolio() {
        guidance.save(portfolioId, "primera");
        var saved = guidance.save(portfolioId, "última");
        assertThat(saved.revision()).isEqualTo(2);
        var other = portfolios.create("Otra " + System.nanoTime()).id();
        assertThat(guidance.current(other).status()).isEqualTo("NONE");
        assertThat(guidance.claim(other, 900L)).isNull();
        assertThat(generate().status()).isEqualTo("READY");
        org.mockito.Mockito.verify(ai).create(eq(data), any(), eq("última"), any());
        assertThat(guidance.current(portfolioId).status()).isEqualTo("USED");
        generate();
        org.mockito.Mockito.verify(ai).create(eq(data), any());
        assertThat(guidance.save(portfolioId, "última").revision()).isEqualTo(3);
        assertThat(guidance.current(portfolioId).status()).isEqualTo("PENDING");
    }

    @Test
    void newSaveDuringGenerationRemainsPending() {
        guidance.save(portfolioId, "anterior");
        doAnswer(invocation -> { guidance.save(portfolioId, "nueva"); return note; })
                .when(ai).create(eq(data), any(), eq("anterior"), any());
        generate();
        assertThat(guidance.current(portfolioId).text()).isEqualTo("nueva");
        assertThat(guidance.current(portfolioId).status()).isEqualTo("PENDING");
        generate();
        assertThat(guidance.current(portfolioId).status()).isEqualTo("USED");
    }

    @Test
    void aiAndRenderFailuresKeepPendingAndReleaseReservation() {
        guidance.save(portfolioId, "pendiente");
        when(ai.create(any(), any(), anyString(), any())).thenReturn(null);
        assertThat(generate().status()).isEqualTo("READY");
        assertThat(guidance.current(portfolioId).status()).isEqualTo("PENDING");
        when(ai.create(any(), any(), anyString(), any())).thenReturn(note);
        when(renderer.render(any(), any())).thenThrow(new IllegalStateException("render falló"));
        assertThatThrownBy(this::generate).hasMessage("render falló");
        assertThat(guidance.current(portfolioId).status()).isEqualTo("PENDING");
        assertThat(guidanceRepository.findById(portfolioId).orElseThrow().claimedReportId).isNull();
    }

    @Test
    void reservesOnlyOnceAndRecoversExpiredReservationWithoutDuplicateReadyReport() {
        guidance.save(portfolioId, "pendiente");
        var portfolio = portfolios.getPortfolio(portfolioId);
        var first = reportRepository.save(new PortfolioReport(portfolio, data, "MANUAL", "admin"));
        var firstClaim = guidance.claim(portfolioId, first.getId());
        assertThat(firstClaim.text()).isEqualTo("pendiente");
        assertThat(guidance.claim(portfolioId, 800L)).isNull();
        when(clock.instant()).thenReturn(Instant.parse("2026-10-09T20:11:00Z"));
        var second = reportRepository.save(new PortfolioReport(portfolio, data, "MANUAL", "admin"));
        var secondClaim = guidance.claim(portfolioId, second.getId());
        assertThat(secondClaim.revision()).isEqualTo(firstClaim.revision());
        assertThat(reportRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> guidance.ready(first, new PortfolioReportArtifacts(new byte[]{1}, new byte[]{2}), firstClaim, true))
                .hasMessageContaining("expiró");
        guidance.ready(second, new PortfolioReportArtifacts(new byte[]{1}, new byte[]{2}), secondClaim, true);
        assertThat(guidance.current(portfolioId).status()).isEqualTo("USED");
    }

    @Test
    void persistenceFailureDoesNotConsumeGuidance() {
        guidance.save(portfolioId, "pendiente");
        doThrow(new IllegalStateException("persistencia falló")).when(reportRepository).saveAndFlush(any());
        assertThatThrownBy(this::generate).hasMessage("persistencia falló");
        assertThat(guidance.current(portfolioId).status()).isEqualTo("PENDING");
        assertThat(guidanceRepository.findById(portfolioId).orElseThrow().claimedReportId).isNull();
    }

    @Test
    void concurrentGenerationsCannotReserveSameRevision() throws Exception {
        guidance.save(portfolioId, "pendiente");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return guidance.claim(portfolioId, 801L); });
            var second = executor.submit(() -> { start.await(); return guidance.claim(portfolioId, 802L); });
            start.countDown();
            var claims = java.util.Arrays.asList(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(claims.stream().filter(java.util.Objects::nonNull).count()).isEqualTo(1);
        }
    }

    @Test
    void cancellingPendingDoesNotReactivateOnSettingsChangesAndApiRequiresAuthCsrfAndValidText() throws Exception {
        mvc.perform(get("/api/reports/ai-guidance").param("portfolioId", portfolioId.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/reports/ai-guidance").param("portfolioId", portfolioId.toString())
                .with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hola\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/reports/ai-guidance").param("portfolioId", portfolioId.toString())
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/reports/ai-guidance").param("portfolioId", portfolioId.toString())
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hola\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("PENDING"));
        guidance.cancel(portfolioId);
        assertThat(guidance.current(portfolioId).status()).isEqualTo("NONE");
        mvc.perform(put("/api/reports/ai-info").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"model\":\"gpt-5.5\",\"effort\":\"low\"}"))
                .andExpect(status().isOk());
        assertThat(guidance.current(portfolioId).status()).isEqualTo("NONE");
    }
}
