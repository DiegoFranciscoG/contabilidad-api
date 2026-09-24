package io.github.diegofranciscog.accounting.service.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

/**
 * Writes typed cells only (numbers as numbers, text as text), never formulas, so values coming from users
 * such as a description starting with "=" cannot become spreadsheet formulas.
 */
@Component
public class ExcelExporter {

    public byte[] export(TabularReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");
            Styles styles = new Styles(workbook);

            int rowIndex = 0;
            createTextRow(sheet, rowIndex++, report.title(), styles.title);
            createTextRow(sheet, rowIndex++, report.subtitle(), styles.text);
            rowIndex++;

            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < report.columns().size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(report.columns().get(i).header());
                cell.setCellStyle(styles.header);
            }
            for (TabularReport.Row data : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.cells().size(); i++) {
                    writeCell(row.createCell(i), data.cells().get(i), data.emphasis(), styles);
                }
            }
            for (int i = 0; i < report.columns().size(); i++) {
                sheet.setColumnWidth(i, Math.min(255, report.columns().get(i).width()) * 256);
            }
            sheet.createFreezePane(0, 4);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    private static void createTextRow(Sheet sheet, int index, String value, CellStyle style) {
        Cell cell = sheet.createRow(index).createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void writeCell(Cell cell, Object value, boolean emphasis, Styles styles) {
        if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(emphasis ? styles.numberBold : styles.number);
        } else if (value != null) {
            cell.setCellValue(value.toString());
            cell.setCellStyle(emphasis ? styles.textBold : styles.text);
        }
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle textBold;
        private final CellStyle number;
        private final CellStyle numberBold;

        private Styles(XSSFWorkbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            short money = workbook.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00");

            title = workbook.createCellStyle();
            title.setFont(big);
            header = workbook.createCellStyle();
            header.setFont(bold);
            text = workbook.createCellStyle();
            textBold = workbook.createCellStyle();
            textBold.setFont(bold);
            number = workbook.createCellStyle();
            number.setDataFormat(money);
            numberBold = workbook.createCellStyle();
            numberBold.setDataFormat(money);
            numberBold.setFont(bold);
        }
    }
}
