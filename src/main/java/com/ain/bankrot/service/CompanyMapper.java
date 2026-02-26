package com.ain.bankrot.service;

import com.ain.bankrot.model.legal.LegalEntityRow;
import com.ain.bankrot.util.Dates;
import com.ain.bankrot.util.RegionExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CompanyMapper {

    private final ObjectMapper mapper = new ObjectMapper();

    public LegalEntityRow fromCompanyJson(String json, String sourceUrl) throws Exception {
        JsonNode root = mapper.readTree(json);

        LegalEntityRow row = new LegalEntityRow();
        row.sourceUrl = sourceUrl;

        // --- идентификаторы ---
        row.fullName = text(root, "fullName", "name", "shortName");
        row.inn      = text(root, "inn");
        row.ogrn     = text(root, "ogrn");
        row.kpp      = text(root, "kpp");

        // --- адрес ---
        row.address = firstNonBlank(
                text(root, "address"),
                text(root, "addressFgu"),
                text(root, "addressEgrul"),
                text(root.path("address"), "fullAddress", "value")
        );

        // --- регион ---
        row.region = firstNonBlank(
                text(root, "region"),
                text(root.path("address"), "region", "regionName"),
                RegionExtractor.extract(row.address)
        );

        // --- ОКВЭД ---
        row.okved = joinCodeName(
                text(root.path("okved"), "code"),
                text(root.path("okved"), "name")
        );
        if (row.okved.isBlank()) {
            row.okved = text(root, "okved", "okvedMain", "okvedCode");
        }

        // --- ОКОПФ/ОПФ ---
        row.legalForm = joinCodeName(
                text(root.path("okopf"), "code"),
                text(root.path("okopf"), "name")
        );
        if (row.legalForm.isBlank()) {
            row.legalForm = text(root, "okopf", "legalForm");
        }

        // --- статус ---
        row.status = firstNonBlank(
                text(root.path("status"), "name"),
                text(root, "status")
        );

        // ✅ ДАТА РЕГИСТРАЦИИ (расширили набор ключей)
        String regIso = firstNonBlank(
                text(root, "dateReg"),
                text(root, "registrationDate", "regDate", "dateRegistration"),
                text(root, "egrulDateCreate", "dateCreate", "dateCreated")
        );

        row.registrationDate = toDdMmYyyyFlexible(regIso);

        // ✅ ДАТА ЗАВЕРШЕНИЯ ДЕЛА — ТОЛЬКО из status.date
        String endIso = text(root.path("status"), "date");
        row.caseEndDate = toDdMmYyyyFlexible(endIso);

        // --- уставный капитал ---
        row.authorizedCapital = firstNonBlank(
                text(root, "authorizedCapital", "capital"),
                root.has("authorizedCapital") ? root.get("authorizedCapital").asText("") : ""
        );

        return row;
    }

    // ---------- helpers ----------

    private static String toDdMmYyyyFlexible(String iso) {
        if (iso == null) return "";
        String s = iso.trim();
        if (s.isEmpty()) return "";

        // если пришло без времени: 2020-01-31
        if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return Dates.toDdMmYyyyFromIsoDateTime(s + "T00:00:00");
        }

        // если пришло с временем
        if (s.contains("T")) {
            return Dates.toDdMmYyyyFromIsoDateTime(s);
        }

        // непонятный формат — оставим как есть (но без мусора)
        return s;
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                String s = v.asText("");
                if (s != null && !s.isBlank()) return s.trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) if (x != null && !x.isBlank()) return x.trim();
        return "";
    }

    private static String joinCodeName(String code, String name) {
        code = code == null ? "" : code.trim();
        name = name == null ? "" : name.trim();
        if (code.isBlank() && name.isBlank()) return "";
        if (code.isBlank()) return name;
        if (name.isBlank()) return code;
        return code + " — " + name;
    }
}