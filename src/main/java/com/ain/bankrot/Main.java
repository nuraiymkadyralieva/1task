package com.ain.bankrot;

import com.ain.bankrot.api.ApiClient;
import com.ain.bankrot.api.FedresursEndpoints;
import com.ain.bankrot.excel.ExcelExporter;
import com.ain.bankrot.model.legal.LegalEntityRow;
import com.ain.bankrot.model.physical.PhysicalPersonRow;
import com.ain.bankrot.service.LegalRowBuilder;
import com.ain.bankrot.service.PersonRowBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;

public class Main {

    private static final int HEAD = 350;

    // ✅ включай, если хочешь увидеть, как реально заполняются ФИО/прежняя фамилия у первых N людей
    private static final boolean DEBUG_FIRST_PERSONS = true;
    private static final int DEBUG_PERSONS_LIMIT = 3;

    // ✅ небольшая пауза между запросами
    private static final long SLEEP_MS = 200;

    private static String head(String s) {
        if (s == null) return "null";
        return s.substring(0, Math.min(HEAD, s.length()));
    }

    private static String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }

    private static Map<String, String> referer(String base) {
        return Map.of("Referer", base.endsWith("/") ? base : (base + "/"));
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) if (x != null && !x.isBlank()) return x.trim();
        return "";
    }

    public static void main(String[] args) throws Exception {

        // ✅ заголовки
        Map<String, String> headersBankrot = Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Accept", "application/json, text/plain, */*",
                "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                "Referer", "https://bankrot.fedresurs.ru/",
                "Origin", "https://bankrot.fedresurs.ru",
                "Connection", "keep-alive"
        );

        ApiClient bankrot = new ApiClient("https://bankrot.fedresurs.ru", headersBankrot);

        ApiClient fed = new ApiClient("https://fedresurs.ru", Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Accept", "application/json, text/plain, */*",
                "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                "Referer", "https://fedresurs.ru/",
                "Origin", "https://fedresurs.ru",
                "Connection", "keep-alive"
        ));

        ObjectMapper om = new ObjectMapper();

        LegalRowBuilder legalBuilder = new LegalRowBuilder(fed);
        PersonRowBuilder personBuilder = new PersonRowBuilder(fed);

        // ✅ сколько нужно выгрузить
        int needLegals =500;
        int needPersons = 500;

        // ✅ размер страницы
        int pageSize = 15;

        int exportedLegals = 0;
        int exportedPersons = 0;

        try (ExcelExporter excel = new ExcelExporter()) {

            // ---------------- LEGALS ----------------
            int offset = 0;
            int got = 0;

            while (got < needLegals) {

                String listPath = FedresursEndpoints.listCompanies(pageSize, offset);
                String listJson = bankrot.get(listPath);

                System.out.println("\n=== LIST COMPANIES ===");
                System.out.println("LIST URL = https://bankrot.fedresurs.ru" + listPath);
                System.out.println("LIST RESP head = " + head(listJson));

                JsonNode root = om.readTree(listJson);
                JsonNode arr = root.path("pageData");
                if (!arr.isArray() || arr.size() == 0) break;

                for (JsonNode item : arr) {
                    String guid = safeText(item.path("guid"));
                    String caseNumber = safeText(item.path("lastLegalCase").path("number"));

                    System.out.println("\n--- LEGAL ITEM ---");
                    System.out.println("LEGAL GUID = " + guid);
                    System.out.println("LEGAL caseNumber = " + caseNumber);

                    LegalEntityRow row = legalBuilder.buildFromListItem(item);
                    excel.appendLegal(row);
                    exportedLegals++;

                    got++;
                    if (got >= needLegals) break;
                }

                offset += pageSize;
                sleepQuiet(SLEEP_MS);
            }

            // ---------------- PERSONS ----------------
            offset = 0;
            got = 0;

            int debugShown = 0;

            while (got < needPersons) {

                String listPath = FedresursEndpoints.listPersons(pageSize, offset);
                String listJson = bankrot.get(listPath);

                System.out.println("\n=== LIST PERSONS ===");
                System.out.println("LIST URL = https://bankrot.fedresurs.ru" + listPath);
                System.out.println("LIST RESP head = " + head(listJson));

                JsonNode root = om.readTree(listJson);
                JsonNode arr = root.path("pageData");
                if (!arr.isArray() || arr.size() == 0) break;

                for (JsonNode item : arr) {
                    String guid = safeText(item.path("guid"));
                    String caseNumber = safeText(item.path("lastLegalCase").path("number"));

                    // ✅ ФИО ИЗ СПИСКА (bankrot) — это наш стабильный источник current fullName
                    String fioFromList = firstNonBlank(
                            safeText(item.path("fullName")),
                            safeText(item.path("fio")),
                            safeText(item.path("name")),
                            safeText(item.path("debtor").path("fullName")),
                            safeText(item.path("debtor").path("fio")),
                            safeText(item.path("debtor").path("name"))
                    );

                    System.out.println("\n--- PERSON ITEM ---");
                    System.out.println("PERSON GUID = " + guid);
                    System.out.println("PERSON caseNumber = " + caseNumber);
                    if (!fioFromList.isBlank()) System.out.println("PERSON fioFromList = " + fioFromList);

                    PhysicalPersonRow row = personBuilder.buildFromListItem(item);

                    // ✅ ДУБЛЬ-СТРАХОВКА: если builder вдруг вернул пусто — подставим ФИО из списка
                    if ((row.fullName == null || row.fullName.isBlank()) && !fioFromList.isBlank()) {
                        row.fullName = fioFromList;
                    }

                    // ✅ дебаг первых N физлиц: покажем, заполнилась ли прежняя фамилия
                    if (DEBUG_FIRST_PERSONS && debugShown < DEBUG_PERSONS_LIMIT) {
                        debugShown++;
                        System.out.println("PARSED fullName = " + (row.fullName == null ? "" : row.fullName));
                        System.out.println("PARSED previousSurname = " + (row.previousFullName == null ? "" : row.previousFullName));
                        System.out.println("PARSED inn = " + (row.inn == null ? "" : row.inn));
                        System.out.println("PARSED snils = " + (row.snils == null ? "" : row.snils));
                    }

                    excel.appendPhysical(row);
                    exportedPersons++;

                    got++;
                    if (got >= needPersons) break;

                    sleepQuiet(SLEEP_MS);
                }

                offset += pageSize;
                sleepQuiet(SLEEP_MS);
            }

            excel.saveAtomic(Path.of("fedresurs_debtors_" + System.currentTimeMillis() + ".xlsx"));
        }

        System.out.println("OK: exported " + exportedLegals + " legal + " + exportedPersons + " persons");
    }
}