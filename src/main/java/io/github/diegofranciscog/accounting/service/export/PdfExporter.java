package io.github.diegofranciscog.accounting.service.export;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Component
public class PdfExporter {

    private static final Font TITLE = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font HEADER = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD);

    public byte[] export(TabularReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle page = report.columns().size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
        Document document = new Document(page, 28, 28, 28, 28);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph(report.title(), TITLE));
        Paragraph subtitle = new Paragraph(report.subtitle(), SUBTITLE);
        subtitle.setSpacingAfter(8);
        document.add(subtitle);

        PdfPTable table = new PdfPTable(report.columns().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        float[] widths = new float[report.columns().size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = report.columns().get(i).width();
            PdfPCell cell = new PdfPCell(new Phrase(report.columns().get(i).header(), HEADER));
            cell.setGrayFill(0.9f);
            cell.setHorizontalAlignment(report.columns().get(i).numeric() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        table.setWidths(widths);

        DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        for (TabularReport.Row row : report.rows()) {
            for (int i = 0; i < row.cells().size(); i++) {
                Object value = row.cells().get(i);
                String text = value instanceof BigDecimal number ? money.format(number) : value == null ? "" : value.toString();
                PdfPCell cell = new PdfPCell(new Phrase(text, row.emphasis() ? BODY_BOLD : BODY));
                cell.setBorderWidth(0.3f);
                cell.setHorizontalAlignment(report.columns().get(i).numeric() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(cell);
            }
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }
}
