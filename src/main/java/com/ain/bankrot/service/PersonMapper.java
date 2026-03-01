package com.ain.bankrot.service;

import com.ain.bankrot.model.physical.PhysicalPersonRow;
import com.ain.bankrot.util.Dates;
import com.ain.bankrot.util.RegionExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PersonMapper {

    private final ObjectMapper om = new ObjectMapper();

    public PhysicalPersonRow fromPersonJson(String personJson, String sourceUrl) throws Exception {
        JsonNode p = om.readTree(personJson);

        PhysicalPersonRow row = new PhysicalPersonRow();
        row.sourceUrl = sourceUrl;

        // --- ФИО (УЛУЧШЕННЫЙ разбор) ---
        row.fullName = buildFullName(p);

        // --- идентификаторы ---
        row.inn = firstNonBlank(
                p.path("inn").asText(""),
                findDeep(p, "inn")
        );

        row.snils = firstNonBlank(
                p.path("snils").asText(""),
                findDeep(p, "snils")
        );

        // --- дата и место рождения ---
        row.birthDate = Dates.toDdMmYyyyFromIsoDateTime(
                firstNonBlank(
                        p.path("birthdateBankruptcy").asText(""),
                        p.path("birthDate").asText(""),
                        findDeep(p, "birthdateBankruptcy", "birthDate", "birthdate", "dateOfBirth")
                )
        );

        row.birthPlace = firstNonBlank(
                p.path("birthplaceBankruptcy").asText(""),
                p.path("birthPlace").asText(""),
                findDeep(p, "birthplaceBankruptcy", "birthPlace", "birthplace", "placeOfBirth")
        );

        // --- адрес проживания ---
        row.residenceAddress = firstNonBlank(
                p.path("address").asText(""),
                p.path("residenceAddress").asText(""),
                findDeep(p, "residenceAddress", "address", "fullAddress", "value")
        );

        // --- регион из адреса ---
        row.region = RegionExtractor.extract(row.residenceAddress);

        // --- "предыдущее ФИО" по ТЗ = ПРЕДЫДУЩАЯ ФАМИЛИЯ ---
        row.previousFullName = extractPreviousSurname(p);

        // защита: если предыдущая фамилия = текущая фамилия, очищаем
        String currentSurname = extractSurnameFromFio(row.fullName);
        if (!row.previousFullName.isBlank()
                && !currentSurname.isBlank()
                && row.previousFullName.equalsIgnoreCase(currentSurname)) {
            row.previousFullName = "";
        }

        return row;
    }

    // ================= helpers =================

    /** Собирает ФИО из всех возможных вариантов (включая deep-поиск) */
    private static String buildFullName(JsonNode p) {

        // 0) deep-поиск готовой строки (часто лежит вложенно)
        String deep = firstNonBlank(
                findDeep(p, "fullName", "fio", "name", "personName", "debtorName")
        );
        if (!deep.isBlank()) return normalizeSpaces(deep);

        // 1) прямые поля
        String direct = firstNonBlank(
                p.path("fullName").asText(""),
                p.path("fio").asText(""),
                p.path("name").asText("")
        );
        if (!direct.isBlank()) return normalizeSpaces(direct);

        // 2) раздельные поля на верхнем уровне
        String last = firstNonBlank(p.path("lastName").asText(""), p.path("surname").asText(""));
        String first = firstNonBlank(p.path("firstName").asText(""), p.path("givenName").asText(""));
        String middle = firstNonBlank(p.path("middleName").asText(""), p.path("patronymic").asText(""));

        String combined = normalizeSpaces((last + " " + first + " " + middle));
        if (!combined.isBlank()) return combined;

        // 3) раздельные поля, но вложенные где-то глубже
        String last2 = findDeep(p, "lastName", "surname");
        String first2 = findDeep(p, "firstName", "givenName");
        String middle2 = findDeep(p, "middleName", "patronymic");

        return normalizeSpaces((last2 + " " + first2 + " " + middle2));
    }

    /**
     * По ТЗ: в колонке "Предыдущее ФИО" храним только ПРЕДЫДУЩУЮ ФАМИЛИЮ.
     *
     * ВАЖНО: в твоём реальном JSON `nameHistories` может быть МАССИВОМ СТРОК:
     *   "nameHistories": ["Скрипкина Оксана Васильевна"]
     */
    private static String extractPreviousSurname(JsonNode p) {

        JsonNode histories = p.path("nameHistories");
        if (histories.isArray()) {
            for (JsonNode h : histories) {

                // ✅ A) history как СТРОКА
                if (h.isTextual()) {
                    String prevFio = normalizeSpaces(h.asText(""));
                    if (!prevFio.isBlank()) {
                        return extractSurnameFromFio(prevFio); // -> "Скрипкина"
                    }
                }

                // ✅ B) history как объект, фамилия отдельно
                String prevLast = firstNonBlank(
                        h.path("lastName").asText(""),
                        h.path("surname").asText("")
                );
                if (!prevLast.isBlank()) return normalizeSpaces(prevLast);

                // ✅ C) history как объект, ФИО строкой
                String prevFio = firstNonBlank(
                        h.path("fullName").asText(""),
                        h.path("fio").asText(""),
                        h.path("name").asText("")
                );
                String lastFromFio = extractSurnameFromFio(prevFio);
                if (!lastFromFio.isBlank()) return lastFromFio;

                // ✅ D) history как объект, раздельные части
                String last = firstNonBlank(h.path("lastName").asText(""), h.path("surname").asText(""));
                String first = firstNonBlank(h.path("firstName").asText(""), h.path("givenName").asText(""));
                String middle = firstNonBlank(h.path("middleName").asText(""), h.path("patronymic").asText(""));
                String combined = normalizeSpaces(last + " " + first + " " + middle);
                if (!combined.isBlank()) return extractSurnameFromFio(combined);
            }
        }

        // deep-fallback: ключи именно для фамилии
        String deepLast = firstNonBlank(
                findDeep(p, "previousLastName", "oldLastName", "surnamePrevious", "lastNamePrevious"),
                findDeep(p, "previousSurname", "oldSurname")
        );
        if (!deepLast.isBlank()) return normalizeSpaces(deepLast);

        // deep: если дали "предыдущее ФИО" строкой — берём фамилию как первое слово
        String deepFio = firstNonBlank(
                findDeep(p, "previousFullName", "previousName", "oldName", "fioPrevious", "fullNamePrevious")
        );
        return extractSurnameFromFio(deepFio);
    }

    /** Эвристика RU ФИО: "Фамилия Имя Отчество" -> фамилия = первое слово */
    private static String extractSurnameFromFio(String fio) {
        String s = normalizeSpaces(fio);
        if (s.isBlank()) return "";
        String[] parts = s.split(" ");
        return parts.length > 0 ? parts[0].trim() : "";
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) if (x != null && !x.isBlank()) return x.trim();
        return "";
    }

    // ===== deep-find: ищем ключ где угодно внутри JSON =====
    private static String findDeep(JsonNode root, String... keys) {
        if (root == null || root.isNull() || root.isMissingNode()) return "";
        for (String k : keys) {
            String v = findByKey(root, k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String findByKey(JsonNode node, String key) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;

        if (node.isObject()) {
            JsonNode direct = node.get(key);
            if (direct != null && !direct.isNull()) {
                String s = direct.asText("");
                if (s != null && !s.isBlank()) return s;

                // если это объект с полями name/value
                String name = direct.path("name").asText("");
                if (!name.isBlank()) return name;

                String value = direct.path("value").asText("");
                if (!value.isBlank()) return value;
            }

            var it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                String got = findByKey(e.getValue(), key);
                if (got != null && !got.isBlank()) return got;
            }
        } else if (node.isArray()) {
            for (JsonNode x : node) {
                String got = findByKey(x, key);
                if (got != null && !got.isBlank()) return got;
            }
        }
        return null;
    }
}