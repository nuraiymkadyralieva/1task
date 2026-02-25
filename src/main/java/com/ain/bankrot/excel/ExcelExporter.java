package com.ain.bankrot.excel;

import com.ain.bankrot.model.legal.LegalEntityRow;
import com.ain.bankrot.model.physical.PhysicalPersonRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.List;

public class ExcelExporter implements AutoCloseable {

    private final Workbook wb;
    private final Sheet legalSheet;
    private final Sheet physicalSheet;

    private final CellStyle headerStyle;

    private int legalRowIdx = 1;     // 0 — header
    private int physicalRowIdx = 1;

    public ExcelExporter() {
        this.wb = new XSSFWorkbook();

        // --- styles ---
        this.headerStyle = createHeaderStyle(wb);

        // --- sheets ---
        this.legalSheet = wb.createSheet(Sheets.LEGAL);
        this.physicalSheet = wb.createSheet(Sheets.PHYSICAL);

        writeHeader(legalSheet, Sheets.LEGAL_HEADERS);
        writeHeader(physicalSheet, Sheets.PHYSICAL_HEADERS);

        // Закрепляем шапку
        legalSheet.createFreezePane(0, 1);
        physicalSheet.createFreezePane(0, 1);

        // Фильтр по заголовкам
        legalSheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, 0, 0, Sheets.LEGAL_HEADERS.size() - 1
        ));
        physicalSheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, 0, 0, Sheets.PHYSICAL_HEADERS.size() - 1
        ));
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);

        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private void writeHeader(Sheet sheet, List<String> headers) {
        Row r = sheet.createRow(0);
        r.setHeightInPoints(18);

        for (int i = 0; i < headers.size(); i++) {
            Cell c = r.createCell(i, CellType.STRING);
            c.setCellValue(headers.get(i));
            c.setCellStyle(headerStyle);
        }
    }

    /**
     * ✅ FIX: Всегда создаём ячейку, даже если значение пустое.
     * Иначе Excel получает "дырки" (ячейки физически нет), и некоторые колонки
     * потом выглядят как "вечно пустые".
     */
    private void setCell(Row row, int col, String value) {
        Cell cell = row.createCell(col, CellType.STRING);
        cell.setCellValue(value == null ? "" : value.trim());
    }

    public void appendLegal(LegalEntityRow x) {
        Row r = legalSheet.createRow(legalRowIdx++);
        int c = 0;
        setCell(r, c++, x.fullName);
        setCell(r, c++, x.inn);
        setCell(r, c++, x.ogrn);
        setCell(r, c++, x.kpp);
        setCell(r, c++, x.authorizedCapital);
        setCell(r, c++, x.registrationDate);
        setCell(r, c++, x.address);
        setCell(r, c++, x.region);
        setCell(r, c++, x.legalForm);
        setCell(r, c++, x.okved);
        setCell(r, c++, x.status);
        setCell(r, c++, x.procedureType);
        setCell(r, c++, x.caseNumber);
        setCell(r, c++, x.caseStatus);
        setCell(r, c++, x.caseEndDate);
        setCell(r, c++, x.arbitrationManagerName);
        setCell(r, c++, x.arbitrationManagerInn);
        setCell(r, c++, x.managerAppointmentDate);
        setCell(r, c++, x.publicationsCount);
        setCell(r, c++, x.tradesCount);
        setCell(r, c++, x.sourceUrl);
    }

    public void appendPhysical(PhysicalPersonRow x) {
        Row r = physicalSheet.createRow(physicalRowIdx++);
        int c = 0;
        setCell(r, c++, x.fullName);
        setCell(r, c++, x.previousFullName);
        setCell(r, c++, x.inn);
        setCell(r, c++, x.snils);
        setCell(r, c++, x.birthDate);
        setCell(r, c++, x.birthPlace);
        setCell(r, c++, x.residenceAddress);
        setCell(r, c++, x.region);
        setCell(r, c++, x.entrepreneurOgrnip);
        setCell(r, c++, x.entrepreneurStatus);
        setCell(r, c++, x.okved);
        setCell(r, c++, x.registrationDate);
        setCell(r, c++, x.terminationDate);
        setCell(r, c++, x.bankruptcyStatus);
        setCell(r, c++, x.procedureType);
        setCell(r, c++, x.caseNumber);
        setCell(r, c++, x.arbitrationManagerName);
        setCell(r, c++, x.sourceUrl);
    }

    public void autosizeAll() {
        autosize(legalSheet, Sheets.LEGAL_HEADERS.size());
        autosize(physicalSheet, Sheets.PHYSICAL_HEADERS.size());
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int max = 255 * 256;
            if (width > max) sheet.setColumnWidth(i, max);
        }
    }

    /** Надежная запись: temp -> move */
    public void saveAtomic(Path target) throws IOException {
        autosizeAll();

        Path parent = target.getParent();
        if (parent == null) parent = Path.of(".");
        Files.createDirectories(parent);

        Path tmp = parent.resolve(target.getFileName().toString() + ".tmp");

        try (OutputStream os = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            wb.write(os);
            os.flush();
        }

        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void close() throws IOException {
        wb.close();
    }
}