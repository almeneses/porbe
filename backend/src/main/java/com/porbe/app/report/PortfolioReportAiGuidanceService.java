package com.porbe.app.report;

import com.porbe.app.portfolio.Portfolio;
import com.porbe.app.portfolio.PortfolioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserva una revisión sin mantener bloqueos durante la llamada a IA o Chromium. */
@Service
public class PortfolioReportAiGuidanceService {
    private final PortfolioReportAiGuidanceRepository repository;
    private final PortfolioReportRepository reportRepository;
    private final PortfolioService portfolioService;
    private final EntityManager entityManager;
    private final Clock clock;

    public PortfolioReportAiGuidanceService(PortfolioReportAiGuidanceRepository repository,
            PortfolioReportRepository reportRepository, PortfolioService portfolioService,
            EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.reportRepository = reportRepository;
        this.portfolioService = portfolioService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioReportAiGuidanceResponse current(Long portfolioId) {
        var portfolio = portfolioService.getPortfolio(portfolioId);
        return repository.findById(portfolio.getId()).map(this::response)
                .orElse(new PortfolioReportAiGuidanceResponse("", "NONE", 0, null));
    }

    @Transactional
    public PortfolioReportAiGuidanceResponse save(Long portfolioId, String text) {
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw new IllegalArgumentException("Escribe una indicación de máximo 2000 caracteres.");
        }
        var guidance = locked(portfolioId);
        guidance.text = text.trim();
        guidance.revision++;
        guidance.updatedAt = now();
        return response(guidance);
    }

    @Transactional
    public PortfolioReportAiGuidanceResponse cancel(Long portfolioId) {
        var guidance = locked(portfolioId);
        guidance.text = "";
        guidance.usedRevision = ++guidance.revision;
        guidance.updatedAt = now();
        return response(guidance);
    }

    @Transactional
    public Claim claim(Long portfolioId, Long reportId) {
        var guidance = locked(portfolioId);
        // shortcut: reserva de 10 minutos; ampliar si IA y render necesitan más tiempo.
        if (guidance.claimedReportId != null && guidance.claimedAt.isBefore(now().minusMinutes(10))) {
            reportRepository.findById(guidance.claimedReportId).ifPresent(report -> {
                if ("GENERATING".equals(report.getStatus())) {
                    report.markFailed("La reserva de la indicación de IA expiró; genera nuevamente el informe.");
                }
            });
            guidance.release();
        }
        if (!guidance.pending() || guidance.claimedReportId != null) return null;
        guidance.claimedReportId = reportId;
        guidance.claimedAt = now();
        return new Claim(guidance.text, guidance.revision, reportId);
    }

    /** Guarda READY y consume la revisión en la misma transacción: ambos cambios o ninguno. */
    @Transactional
    public PortfolioReport ready(PortfolioReport report, PortfolioReportArtifacts artifacts, Claim claim, boolean hasNote) {
        var guidance = claim == null ? null : locked(report.getPortfolio().getId());
        if (claim != null && !claim.reportId().equals(guidance.claimedReportId)) {
            throw new IllegalStateException("La reserva de la indicación de IA expiró; genera nuevamente el informe.");
        }
        report.markReady(artifacts.image(), artifacts.pdf(), now());
        var saved = reportRepository.saveAndFlush(report);
        if (guidance != null) {
            if (hasNote) guidance.usedRevision = Math.max(guidance.usedRevision, claim.revision());
            guidance.release();
        }
        return saved;
    }

    @Transactional
    public void release(Long portfolioId, Claim claim) {
        if (claim == null) return;
        var guidance = locked(portfolioId);
        if (claim.reportId().equals(guidance.claimedReportId)) guidance.release();
    }

    private PortfolioReportAiGuidance locked(Long portfolioId) {
        var portfolio = portfolioService.getPortfolio(portfolioId);
        // El portafolio también existe antes del primer guardado: serializa la creación de la fila.
        entityManager.lock(entityManager.find(Portfolio.class, portfolio.getId()), LockModeType.PESSIMISTIC_WRITE);
        var existing = repository.findById(portfolio.getId());
        if (existing.isPresent()) {
            var guidance = existing.orElseThrow();
            entityManager.refresh(guidance);
            return guidance;
        }
        return repository.save(new PortfolioReportAiGuidance(portfolio.getId(), now()));
    }

    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private PortfolioReportAiGuidanceResponse response(PortfolioReportAiGuidance guidance) {
        return new PortfolioReportAiGuidanceResponse(guidance.text,
                guidance.text.isBlank() ? "NONE" : guidance.pending() ? "PENDING" : "USED",
                guidance.revision, guidance.updatedAt);
    }

    public record Claim(String text, long revision, Long reportId) { }
}
