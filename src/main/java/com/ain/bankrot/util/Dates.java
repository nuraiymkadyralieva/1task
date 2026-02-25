package com.ain.bankrot.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class Dates {
    private Dates() {}

    public static final DateTimeFormatter EXCEL_DDMMYYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static String toDdMmYyyy(LocalDate d) {
        return d == null ? "" : EXCEL_DDMMYYYY.format(d);
    }

    // Часто в JSON приходит "1962-08-30T00:00:00" или ISO-строка
    public static String toDdMmYyyyFromIsoDateTime(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            // 2020-01-01T00:00:00 or with offset
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            return toDdMmYyyy(odt.toLocalDate());
        } catch (Exception ignore) {
            // fallback: first 10 chars "YYYY-MM-DD"
            try {
                return toDdMmYyyy(LocalDate.parse(iso.substring(0, 10)));
            } catch (Exception e) {
                return "";
            }
        }
    }
}
