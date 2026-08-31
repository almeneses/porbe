package com.porbe.app.operation;

import com.porbe.app.importer.ImportBatch;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.importer.ImportSourceType;
import com.porbe.app.portfolio.PortfolioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Administra el libro manual, los lotes importados y su trazabilidad. */
@Service
public class OperationManagementService {

    private static final int MAX_RESPONSE_ROWS = 1000;

    private final PortfolioService portfolioService;
    private final PortfolioOperationRepository operationRepository;
    private final ImportBatchRepository importBatchRepository;
    private final OperationAuditRepository auditRepository;
    private final OperationRules operationRules;
    private final ObjectMapper objectMapper;

    public OperationManagementService(
            PortfolioService portfolioService,
            PortfolioOperationRepository operationRepository,
            ImportBatchRepository importBatchRepository,
            OperationAuditRepository auditRepository,
            OperationRules operationRules,
            ObjectMapper objectMapper) {
        this.portfolioService = portfolioService;
        this.operationRepository = operationRepository;
        this.importBatchRepository = importBatchRepository;
        this.auditRepository = auditRepository;
        this.operationRules = operationRules;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OperationsResponse list(OperationFilter filter) {
        validateRange(filter);
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var allOperations = operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio);
        var consistency = consistencyByOperation(allOperations);
        var filtered = allOperations.stream()
                .filter(operation -> matches(operation, filter))
                .sorted(Comparator.comparing(PortfolioOperation::getDate)
                        .thenComparing(PortfolioOperation::getId)
                        .reversed())
                .toList();
        var responses = filtered.stream()
                .limit(MAX_RESPONSE_ROWS)
                .map(operation -> response(operation, consistency.get(operation.getId())))
                .toList();
        return new OperationsResponse(filtered.size(), responses.size(), responses);
    }

    @Transactional
    public OperationResponse create(OperationRequest request, String username) {
        var data = validated(request);
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                "Operación manual",
                "manual-" + UUID.randomUUID(),
                1,
                username,
                ImportSourceType.MANUAL));
        var operation = operationRepository.saveAndFlush(new PortfolioOperation(
                portfolio,
                batch,
                data.date(),
                data.type(),
                data.ticker(),
                data.name(),
                data.quantity(),
                data.unitPrice(),
                data.commission(),
                data.totalAmount(),
                data.notes()));
        auditRepository.save(new OperationAudit(
                operation.getId(),
                batch.getId(),
                OperationAuditAction.CREATED,
                username,
                "Creó manualmente " + operationLabel(operation) + ".",
                null,
                json(OperationSnapshot.from(operation))));
        return responseWithConsistency(operation);
    }

    @Transactional
    public OperationResponse update(Long id, OperationRequest request, String username) {
        var operation = findOperation(id);
        var previous = json(OperationSnapshot.from(operation));
        var data = validated(request);
        operation.update(
                data.date(),
                data.type(),
                data.ticker(),
                data.name(),
                data.quantity(),
                data.unitPrice(),
                data.commission(),
                data.totalAmount(),
                data.notes(),
                username);
        operationRepository.saveAndFlush(operation);
        auditRepository.save(new OperationAudit(
                operation.getId(),
                operation.getImportBatch().getId(),
                OperationAuditAction.UPDATED,
                username,
                "Modificó " + operationLabel(operation) + ".",
                previous,
                json(OperationSnapshot.from(operation))));
        return responseWithConsistency(operation);
    }

    @Transactional
    public void delete(Long id, String username) {
        var operation = findOperation(id);
        var batch = operation.getImportBatch();
        auditRepository.save(new OperationAudit(
                operation.getId(),
                batch.getId(),
                OperationAuditAction.DELETED,
                username,
                "Eliminó " + operationLabel(operation) + ".",
                json(OperationSnapshot.from(operation)),
                null));
        operationRepository.delete(operation);
        operationRepository.flush();
        if (batch.getSourceType() == ImportSourceType.MANUAL
                && operationRepository.countByImportBatch(batch) == 0) {
            importBatchRepository.delete(batch);
        }
    }

    @Transactional
    public List<OperationBatchResponse> importedBatches() {
        return importBatchRepository.findAllBySourceTypeOrderByImportedAtDesc(ImportSourceType.IMPORT).stream()
                .map(batch -> new OperationBatchResponse(
                        batch.getId(),
                        batch.getSourceFilename(),
                        batch.getRowCount(),
                        operationRepository.countByImportBatch(batch),
                        batch.getImportedBy(),
                        batch.getImportedAt()))
                .toList();
    }

    @Transactional
    public void revertBatch(Long id, String username) {
        var batch = importBatchRepository.findById(id)
                .orElseThrow(() -> new OperationResourceNotFoundException("La importación ya no existe."));
        if (batch.getSourceType() != ImportSourceType.IMPORT) {
            throw new IllegalArgumentException("Solo se pueden revertir lotes importados desde Excel.");
        }
        var operations = operationRepository.findAllByImportBatchOrderByDateAscIdAsc(batch);
        var snapshot = json(operations.stream().map(OperationSnapshot::from).toList());
        auditRepository.save(new OperationAudit(
                null,
                batch.getId(),
                OperationAuditAction.BATCH_REVERTED,
                username,
                "Revirtió " + batch.getSourceFilename() + " con " + operations.size() + " operaciones.",
                snapshot,
                null));
        operationRepository.deleteAllByImportBatch(batch);
        operationRepository.flush();
        importBatchRepository.delete(batch);
    }

    @Transactional(readOnly = true)
    public List<OperationAuditResponse> auditTrail() {
        return auditRepository.findTop100ByOrderByCreatedAtDescIdDesc().stream()
                .map(OperationAuditResponse::from)
                .toList();
    }

    @Transactional
    public List<PortfolioOperation> filteredOperations(OperationFilter filter) {
        validateRange(filter);
        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        return operationRepository.findAllByPortfolioOrderByDateAscIdAsc(portfolio).stream()
                .filter(operation -> matches(operation, filter))
                .toList();
    }

    private OperationData validated(OperationRequest request) {
        var data = operationRules.normalize(request);
        var errors = operationRules.validate(data);
        if (!errors.isEmpty()) {
            throw new OperationValidationException(errors);
        }
        return data;
    }

    /** Recorre todo el libro para señalar exactamente el movimiento que deja saldo negativo. */
    private Map<Long, ConsistencyState> consistencyByOperation(List<PortfolioOperation> operations) {
        var quantities = new LinkedHashMap<String, BigDecimal>();
        var states = new LinkedHashMap<Long, ConsistencyState>();
        for (var operation : operations) {
            if (operation.getTicker() == null
                    || operation.getQuantity() == null
                    || (operation.getType() != OperationType.COMPRA && operation.getType() != OperationType.VENTA)) {
                continue;
            }
            var ticker = normalizeTicker(operation.getTicker());
            var current = quantities.getOrDefault(ticker, BigDecimal.ZERO);
            var after = operation.getType() == OperationType.COMPRA
                    ? current.add(operation.getQuantity())
                    : current.subtract(operation.getQuantity());
            String issue = null;
            if (after.signum() < 0) {
                issue = operation.getType() == OperationType.VENTA
                        ? "Esta venta supera la cantidad disponible y deja la posición negativa."
                        : "La posición continúa negativa después de esta compra.";
            }
            quantities.put(ticker, after);
            states.put(operation.getId(), new ConsistencyState(after, issue));
        }
        return states;
    }

    private OperationResponse responseWithConsistency(PortfolioOperation selected) {
        var operations = operationRepository.findAllByPortfolioOrderByDateAscIdAsc(selected.getPortfolio());
        return response(selected, consistencyByOperation(operations).get(selected.getId()));
    }

    private OperationResponse response(PortfolioOperation operation, ConsistencyState state) {
        return OperationResponse.from(
                operation,
                state == null ? null : state.quantityAfter(),
                state == null ? null : state.issue());
    }

    private boolean matches(PortfolioOperation operation, OperationFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.from() != null && operation.getDate().isBefore(filter.from())) {
            return false;
        }
        if (filter.to() != null && operation.getDate().isAfter(filter.to())) {
            return false;
        }
        if (filter.type() != null && operation.getType() != filter.type()) {
            return false;
        }
        if (filter.sourceType() != null
                && operation.getImportBatch().getSourceType() != filter.sourceType()) {
            return false;
        }
        if (filter.importBatchId() != null
                && !filter.importBatchId().equals(operation.getImportBatch().getId())) {
            return false;
        }
        if (filter.ticker() != null && !filter.ticker().isBlank()) {
            var expected = filter.ticker().trim().toUpperCase(Locale.ROOT);
            return operation.getTicker() != null
                    && normalizeTicker(operation.getTicker()).contains(expected);
        }
        return true;
    }

    private void validateRange(OperationFilter filter) {
        if (filter != null && filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private PortfolioOperation findOperation(Long id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> new OperationResourceNotFoundException("La operación ya no existe."));
    }

    private String operationLabel(PortfolioOperation operation) {
        return "la operación " + operation.getType().label() + " de "
                + (operation.getTicker() == null ? "efectivo" : operation.getTicker());
    }

    private String normalizeTicker(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("No fue posible registrar la trazabilidad de la operación.", exception);
        }
    }

    private record ConsistencyState(BigDecimal quantityAfter, String issue) {
    }

    /** Copia estable de los campos financieros guardada en la bitácora. */
    private record OperationSnapshot(
            Long id,
            LocalDate date,
            String type,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal commission,
            BigDecimal totalAmount,
            String notes,
            OffsetDateTime updatedAt,
            String updatedBy) {

        static OperationSnapshot from(PortfolioOperation operation) {
            return new OperationSnapshot(
                    operation.getId(),
                    operation.getDate(),
                    operation.getType().name(),
                    operation.getTicker(),
                    operation.getName(),
                    operation.getQuantity(),
                    operation.getUnitPrice(),
                    operation.getCommission(),
                    operation.getTotalAmount(),
                    operation.getNotes(),
                    operation.getUpdatedAt(),
                    operation.getUpdatedBy());
        }
    }
}
