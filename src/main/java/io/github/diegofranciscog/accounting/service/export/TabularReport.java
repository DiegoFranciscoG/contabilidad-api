package io.github.diegofranciscog.accounting.service.export;

import java.util.List;

/**
 * Format-neutral table that both exporters render. Cells are String or BigDecimal;
 * {@code emphasis} marks subtotal/total rows that are printed in bold.
 */
public record TabularReport(String title, String subtitle, String fileName, List<Column> columns, List<Row> rows) {

    public record Column(String header, boolean numeric, int width) {
    }

    public record Row(List<Object> cells, boolean emphasis) {
    }
}
