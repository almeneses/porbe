package com.porbe.app.importer;

import com.porbe.app.operation.OperationType;
import com.porbe.app.operation.PortfolioOperation;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.portfolio.PortfolioService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
/** Valida un Excel de movimientos y persiste el lote de forma atómica. */
public class PortfolioImportService {

    private static final String OPERATIONS_SHEET = "Operaciones";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");
    private static final Pattern TICKER_PATTERN = Pattern.compile("[A-Z0-9^][A-Z0-9.^=\\-]{0,29}");
    private static final List<DateTimeFormatter> ACCEPTED_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.forLanguageTag("es-CO"))
                    .withResolverStyle(ResolverStyle.STRICT),
                    DateTimeFormatter.ofPattern("dd/MMM/uuuu", Locale.forLanguageTag("es-CO"))
                    .withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE);
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "fecha",
            "operacion",
            "ticker",
            "nombre",
            "cantidad",
            "precio unitario",
            "comision",
            "total del movimiento",
            "notas");

    private final PortfolioService portfolioService;
    private final ImportBatchRepository importBatchRepository;
    private final PortfolioOperationRepository operationRepository;
    private final int maxRows;

    public PortfolioImportService(
            PortfolioService portfolioService,
            ImportBatchRepository importBatchRepository,
            PortfolioOperationRepository operationRepository,
            @Value("${app.import.max-rows:5000}") int maxRows) {
        this.portfolioService = portfolioService;
        this.importBatchRepository = importBatchRepository;
        this.operationRepository = operationRepository;
        this.maxRows = maxRows;
    }

    @Transactional
    /**
     * Rechaza duplicados y valida todo el libro antes de guardar el lote y sus
     * operaciones, de modo que nunca queden importaciones parciales.
     */
    public PortfolioImportResult importWorkbook(MultipartFile file, String username) {
        var bytes = readAndValidateFile(file);
        var fileHash = sha256(bytes);
        if (importBatchRepository.existsByFileHash(fileHash)) {
            throw new DuplicateImportException();
        }

        var parsed = parseWorkbook(bytes);
        if (!parsed.errors().isEmpty()) {
            throw new PortfolioImportValidationException(
                    PortfolioImportResult.rejected(parsed.totalRows(), parsed.errors()));
        }

        var portfolio = portfolioService.getOrCreateDefaultPortfolio();
        var batch = importBatchRepository.save(new ImportBatch(
                portfolio,
                safeFilename(file.getOriginalFilename()),
                fileHash,
                parsed.operations().size(),
                username));

        var operations = parsed.operations().stream()
                .map(item -> new PortfolioOperation(
                        portfolio,
                        batch,
                        item.date(),
                        item.type(),
                        item.ticker(),
                        item.name(),
                        item.quantity(),
                        item.unitPrice(),
                        item.commission(),
                        item.totalAmount(),
                        item.notes()))
                .toList();
        operationRepository.saveAll(operations);

        return PortfolioImportResult.completed(operations.size(), batch.getId());
    }

    private byte[] readAndValidateFile(MultipartFile file) {
        var errors = new ArrayList<ImportRowError>();
        if (file == null || file.isEmpty()) {
            errors.add(new ImportRowError(null, "archivo", "Selecciona un archivo .xlsx con operaciones."));
            throw new PortfolioImportValidationException(PortfolioImportResult.rejected(0, errors));
        }
        var filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            errors.add(new ImportRowError(null, "archivo", "El archivo debe estar guardado en formato .xlsx."));
            throw new PortfolioImportValidationException(PortfolioImportResult.rejected(0, errors));
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("No fue posible leer el archivo cargado.", exception);
        }
    }

    /** Recorre solo filas con contenido y acumula todos los errores encontrados. */
    private ParsedWorkbook parseWorkbook(byte[] bytes) {
        var errors = new ArrayList<ImportRowError>();
        var operations = new ArrayList<ParsedOperation>();
        var totalRows = 0;

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet(OPERATIONS_SHEET);
            if (sheet == null) {
                errors.add(new ImportRowError(null, "hoja", "El archivo debe contener una hoja llamada Operaciones."));
                return new ParsedWorkbook(0, operations, errors);
            }

            var columns = readHeaders(sheet, errors);
            if (!errors.isEmpty()) {
                return new ParsedWorkbook(0, operations, errors);
            }

            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var row = sheet.getRow(rowIndex);
                if (isImportRowEmpty(row, columns, evaluator)) {
                    continue;
                }
                totalRows++;
                if (totalRows > maxRows) {
                    errors.add(new ImportRowError(
                            rowIndex + 1,
                            "archivo",
                            "El archivo supera el máximo de " + maxRows + " operaciones por importación."));
                    break;
                }
                var parsedOperation = parseRow(row, rowIndex + 1, columns, evaluator, errors);
                if (parsedOperation != null) {
                    operations.add(parsedOperation);
                }
            }
        } catch (Exception exception) {
            errors.add(new ImportRowError(
                    null,
                    "archivo",
                    "No fue posible abrir el archivo. Descarga una plantilla nueva y guárdala como .xlsx."));
        }

        if (totalRows == 0 && errors.isEmpty()) {
            errors.add(new ImportRowError(null, "archivo", "La hoja Operaciones no contiene filas para importar."));
        }
        return new ParsedWorkbook(totalRows, operations, errors);
    }

    private Map<String, Integer> readHeaders(Sheet sheet, List<ImportRowError> errors) {
        var columns = new LinkedHashMap<String, Integer>();
        var header = sheet.getRow(0);
        if (header == null) {
            errors.add(new ImportRowError(1, "encabezados", "No se encontraron los encabezados de la plantilla."));
            return columns;
        }
        for (Cell cell : header) {
            var normalized = normalize(cell.getStringCellValue());
            if (!normalized.isBlank()) {
                columns.put(normalized, cell.getColumnIndex());
            }
        }
        REQUIRED_HEADERS.stream()
                .filter(required -> !columns.containsKey(required))
                .sorted()
                .forEach(required -> errors.add(new ImportRowError(
                        1,
                        required,
                        "Falta el encabezado obligatorio: " + displayHeader(required) + ".")));
        return columns;
    }

    /** Normaliza una fila y solo construye la operación cuando todos sus campos son válidos. */
    private ParsedOperation parseRow(
            Row row,
            int rowNumber,
            Map<String, Integer> columns,
            FormulaEvaluator evaluator,
            List<ImportRowError> errors) {
        var initialErrors = errors.size();
        var date = readDate(cell(row, columns, "fecha"), evaluator, rowNumber, errors);
        var operationText = readText(cell(row, columns, "operacion"), evaluator);
        var type = OperationType.fromSpreadsheet(operationText).orElse(null);
        if (type == null) {
            errors.add(new ImportRowError(
                    rowNumber,
                    "operación",
                    "Use compra, venta, dividendo, depósito o retiro."));
        }

        var ticker = uppercaseOrNull(readText(cell(row, columns, "ticker"), evaluator));
        var name = nullIfBlank(readText(cell(row, columns, "nombre"), evaluator));
        var quantity = readDecimal(cell(row, columns, "cantidad"), evaluator, rowNumber, "cantidad", false, errors);
        var unitPrice = readDecimal(cell(row, columns, "precio unitario"), evaluator, rowNumber, "precio unitario", false, errors);
        var commission = readDecimal(cell(row, columns, "comision"), evaluator, rowNumber, "comisión", true, errors);
        var totalAmount = readDecimal(
                cell(row, columns, "total del movimiento"),
                evaluator,
                rowNumber,
                "total del movimiento",
                false,
                errors);
        var notes = nullIfBlank(readText(cell(row, columns, "notas"), evaluator));

        if (date != null && date.isAfter(LocalDate.now(BUSINESS_ZONE))) {
            errors.add(new ImportRowError(rowNumber, "fecha", "La fecha no puede estar en el futuro."));
        }
        if (ticker != null && !TICKER_PATTERN.matcher(ticker).matches()) {
            errors.add(new ImportRowError(rowNumber, "ticker", "El ticker no tiene un formato Yahoo Finance válido."));
        }
        if (name != null && name.length() > 160) {
            errors.add(new ImportRowError(rowNumber, "nombre", "El nombre no puede superar 160 caracteres."));
        }
        if (notes != null && notes.length() > 1000) {
            errors.add(new ImportRowError(rowNumber, "notas", "Las notas no pueden superar 1.000 caracteres."));
        }
        if (quantity != null && (quantity.signum() < 0 || quantity.stripTrailingZeros().scale() > 8)) {
            errors.add(new ImportRowError(rowNumber, "cantidad", "La cantidad debe ser positiva y tener máximo 8 decimales."));
        }
        if (unitPrice != null && (unitPrice.signum() < 0 || unitPrice.stripTrailingZeros().scale() > 8)) {
            errors.add(new ImportRowError(rowNumber, "precio unitario", "El precio debe ser positivo y tener máximo 8 decimales."));
        }
        if (commission == null) {
            commission = BigDecimal.ZERO;
        } else if (commission.signum() < 0) {
            errors.add(new ImportRowError(rowNumber, "comisión", "La comisión no puede ser negativa."));
        }
        if (totalAmount == null || totalAmount.signum() < 0) {
            errors.add(new ImportRowError(rowNumber, "total del movimiento", "El total debe ser un número positivo."));
        }

        if (type != null) {
            validateByType(
                    rowNumber,
                    type,
                    ticker,
                    name,
                    quantity,
                    unitPrice,
                    commission,
                    totalAmount,
                    errors);

            //System.out.println("ValidateByType commented out for testing purposes. Uncomment in production.");
        }

        if (errors.size() > initialErrors) {
            return null;
        }
        return new ParsedOperation(
                date,
                type,
                ticker,
                name,
                quantity,
                unitPrice,
                commission,
                totalAmount,
                notes);
    }

    /** Aplica requisitos y fórmulas diferentes según la naturaleza del movimiento. */
    private void validateByType(
            int rowNumber,
            OperationType type,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal commission,
            BigDecimal totalAmount,
            List<ImportRowError> errors) {
        var stockOperation = type == OperationType.COMPRA
                || type == OperationType.VENTA
                || type == OperationType.DIVIDENDO;
        if (stockOperation && ticker == null) {
            errors.add(new ImportRowError(rowNumber, "ticker", "El ticker es obligatorio para esta operación."));
        }
        if (stockOperation && name == null) {
            errors.add(new ImportRowError(rowNumber, "nombre", "El nombre es obligatorio para esta operación."));
        }

        if (type == OperationType.COMPRA || type == OperationType.VENTA) {
            if (quantity == null) {
                errors.add(new ImportRowError(rowNumber, "cantidad", "La cantidad es obligatoria para compras y ventas."));
            }
            if (unitPrice == null) {
                errors.add(new ImportRowError(rowNumber, "precio unitario", "El precio es obligatorio para compras y ventas."));
            }
            if (quantity != null && unitPrice != null && totalAmount != null) {
                var gross = quantity.multiply(unitPrice);
                var expected = type == OperationType.COMPRA
                        ? gross.add(commission)
                        : gross.subtract(commission);
                validateExpectedTotal(rowNumber, expected, totalAmount, errors);
            }
        }

        if (type == OperationType.DIVIDENDO) {
            if ((quantity == null) != (unitPrice == null)) {
                errors.add(new ImportRowError(
                        rowNumber,
                        "cantidad",
                        "Para dividendos, completa cantidad y precio unitario juntos o deja ambos vacíos."));
            } else if (quantity != null && totalAmount != null) {
                validateExpectedTotal(
                        rowNumber,
                        quantity.multiply(unitPrice).subtract(commission),
                        totalAmount,
                        errors);
            }
        }

        if ((type == OperationType.DEPOSITO || type == OperationType.RETIRO) && commission.signum() != 0) {
            errors.add(new ImportRowError(rowNumber, "comisión", "Depósitos y retiros deben tener comisión igual a 0."));
        }
    }

    private void validateExpectedTotal(
            int rowNumber,
            BigDecimal expected,
            BigDecimal actual,
            List<ImportRowError> errors) {
        if (expected.signum() < 0) {
            errors.add(new ImportRowError(
                    rowNumber,
                    "total del movimiento",
                    "El cálculo de la operación debe producir un total positivo."));
        } else if (expected.subtract(actual).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            errors.add(new ImportRowError(
                    rowNumber,
                    "total del movimiento",
                    "El total no coincide con cantidad × precio y la comisión."));
        }
    }

    private LocalDate readDate(
            Cell cell,
            FormulaEvaluator evaluator,
            int rowNumber,
            List<ImportRowError> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add(new ImportRowError(rowNumber, "fecha", "La fecha es obligatoria."));
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (cell.getCellType() == CellType.FORMULA) {
                var evaluated = evaluator.evaluate(cell);
                if (evaluated != null
                        && evaluated.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(cell)) {
                    return DateUtil.getLocalDateTime(evaluated.getNumberValue()).toLocalDate();
                }
            }
            var text = readText(cell, evaluator);
            for (var formatter : ACCEPTED_DATE_FORMATS) {
                try {
                    return LocalDate.parse(text, formatter);
                } catch (DateTimeParseException ignored) {
                    // Se intenta el siguiente formato de fecha aceptado.
                }
            }
        } catch (RuntimeException ignored) {
            // Más abajo se devuelve un error de fila claro para el usuario.
        }
        errors.add(new ImportRowError(
                rowNumber,
                "fecha",
                "Usa una fecha válida en formato dd/mm/aaaa o aaaa-mm-dd."));
        return null;
    }

    private BigDecimal readDecimal(
            Cell cell,
            FormulaEvaluator evaluator,
            int rowNumber,
            String field,
            boolean blankAsZero,
            List<ImportRowError> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return blankAsZero ? BigDecimal.ZERO : null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            }
            if (cell.getCellType() == CellType.FORMULA) {
                var evaluated = evaluator.evaluate(cell);
                if (evaluated == null || evaluated.getCellType() == CellType.BLANK) {
                    return blankAsZero ? BigDecimal.ZERO : null;
                }
                if (evaluated.getCellType() == CellType.NUMERIC) {
                    return new BigDecimal(NumberToTextConverter.toText(evaluated.getNumberValue()));
                }
                if (evaluated.getCellType() == CellType.STRING) {
                    return new BigDecimal(evaluated.getStringValue().trim());
                }
            }
            var text = readText(cell, evaluator);
            if (text == null || text.isBlank()) {
                return blankAsZero ? BigDecimal.ZERO : null;
            }
            return new BigDecimal(text.trim());
        } catch (RuntimeException exception) {
            errors.add(new ImportRowError(
                    rowNumber,
                    field,
                    "Usa una celda numérica sin símbolos de moneda ni separadores escritos como texto."));
            return null;
        }
    }

    private boolean isImportRowEmpty(
            Row row,
            Map<String, Integer> columns,
            FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        return List.of("fecha", "operacion", "ticker", "nombre", "cantidad", "precio unitario", "total del movimiento", "notas")
                .stream()
                .allMatch(header -> {
                    var value = readText(cell(row, columns, header), evaluator);
                    return value == null || value.isBlank();
                });
    }

    private String readText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return NumberToTextConverter.toText(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA) {
            var evaluated = evaluator.evaluate(cell);
            if (evaluated == null || evaluated.getCellType() == CellType.BLANK) {
                return null;
            }
            return switch (evaluated.getCellType()) {
                case STRING -> evaluated.getStringValue().trim();
                case NUMERIC -> NumberToTextConverter.toText(evaluated.getNumberValue());
                case BOOLEAN -> Boolean.toString(evaluated.getBooleanValue());
                default -> null;
            };
        }
        return cell.toString().trim();
    }

    private Cell cell(Row row, Map<String, Integer> columns, String header) {
        return row == null ? null : row.getCell(columns.get(header), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String displayHeader(String normalized) {
        return switch (normalized) {
            case "operacion" -> "operación";
            case "comision" -> "comisión";
            default -> normalized;
        };
    }

    private String uppercaseOrNull(String value) {
        var clean = nullIfBlank(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "portafolio.xlsx";
        }
        var normalized = filename.replace('\\', '/');
        var safe = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (safe.isBlank()) {
            return "portafolio.xlsx";
        }
        return safe.length() > 255 ? safe.substring(safe.length() - 255) : safe;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    /** Resultado interno de leer el libro antes de iniciar la persistencia. */
    private record ParsedWorkbook(
            int totalRows,
            List<ParsedOperation> operations,
            List<ImportRowError> errors) {
    }

    /** Operación ya normalizada y apta para convertirse en entidad. */
    private record ParsedOperation(
            LocalDate date,
            OperationType type,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal commission,
            BigDecimal totalAmount,
            String notes) {
    }
}
