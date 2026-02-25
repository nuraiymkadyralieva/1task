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

        // --- адрес (у Fedresurs часто строкой) ---
        row.address = firstNonBlank(
                text(root, "address"),
                text(root, "addressFgu"),
                text(root, "addressEgrul"),
                // fallback, если вдруг когда-то станет объектом
                text(root.path("address"), "fullAddress", "value")
        );

        // --- регион: сначала поле (если есть), иначе из адреса ---
        row.region = firstNonBlank(
                text(root, "region"),
                text(root.path("address"), "region", "regionName"),
                RegionExtractor.extract(row.address)
        );

        // --- ОКВЭД: лучше "код — имя" ---
        row.okved = joinCodeName(
                text(root.path("okved"), "code"),
                text(root.path("okved"), "name")
        );
        if (row.okved.isBlank()) {
            // fallback на плоские варианты
            row.okved = text(root, "okved", "okvedMain", "okvedCode");
        }

        // --- ОКОПФ / ОПФ: тоже "код — имя" ---
        row.legalForm = joinCodeName(
                text(root.path("okopf"), "code"),
                text(root.path("okopf"), "name")
        );
        if (row.legalForm.isBlank()) {
            row.legalForm = text(root, "okopf", "legalForm");
        }

        // --- статус (у Fedresurs это объект status.name) ---
        row.status = firstNonBlank(
                text(root.path("status"), "name"),
                text(root, "status") // fallback
        );

        // --- дата регистрации (у Fedresurs: dateReg) ---
        String reg = firstNonBlank(
                text(root, "dateReg"),
                text(root, "registrationDate", "regDate", "dateRegistration")
        );
        row.registrationDate = reg.contains("T")
                ? Dates.toDdMmYyyyFromIsoDateTime(reg)
                : reg;

        // --- уставный капитал ---
        row.authorizedCapital = firstNonBlank(
                text(root, "authorizedCapital", "capital"),
                root.has("authorizedCapital") ? root.get("authorizedCapital").asText("") : ""
        );

        return row;
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
