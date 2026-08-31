package com.porbe.app.operation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;

/** Genera un Excel reutilizable con el estado corregido del libro de operaciones. */
@Service
public class OperationExportService {

    private static final List<String> HEADERS = List.of(
            "fecha",
            "operación",
            "ticker",
            "nombre",
            "cantidad",
            "precio unitario",
            "comisión",
            "total del movimiento",
            "notas");

    public byte[] export(List<PortfolioOperation> operations) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Operaciones");
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));

            var header = sheet.createRow(0);
            for (var index = 0; index < HEADERS.size(); index++) {
                var cell = header.createCell(index);
                cell.setCellValue(HEADERS.get(index));
                cell.setCellStyle(headerStyle);
            }

            for (var index = 0; index < operations.size(); index++) {
                var operation = operations.get(index);
                var row = sheet.createRow(index + 1);
                var dateCell = row.createCell(0);
                dateCell.setCellValue(Date.from(operation.getDate()
                        .atStartOfDay(ZoneId.of("America/Bogota")).toInstant()));
                dateCell.setCellStyle(dateStyle);
                row.createCell(1).setCellValue(operation.getType().label());
                text(row, 2, operation.getTicker());
                text(row, 3, operation.getName());
                number(row, 4, operation.getQuantity());
                number(row, 5, operation.getUnitPrice());
                number(row, 6, operation.getCommission());
                number(row, 7, operation.getTotalAmount());
                text(row, 8, operation.getNotes());
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, operations.size()), 0, 8));
            int[] widths = {14, 16, 20, 28, 16, 18, 14, 22, 40};
            for (var index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, widths[index] * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("No fue posible exportar las operaciones.", exception);
        }
    }

    private void text(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void number(Row row, int column, java.math.BigDecimal value) {
        if (value != null) {
            row.createCell(column).setCellValue(value.doubleValue());
        }
    }
}
