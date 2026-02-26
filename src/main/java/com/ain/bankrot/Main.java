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

    // ✅ включай только когда реально нужно дебажить
    private static final boolean DEBUG_PER_ITEM = false;

    // ✅ сколько элементов разрешаем "дебажить" (чтобы не устроить DDoS)
    private static final int DEBUG_ITEMS_LIMIT = 1;

    // ✅ небольшая пауза между запросами (снижает шанс 451/403)
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

    public static void main(String[] args) throws Exception {

        // ✅ чуть более "браузерные" заголовки
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
        int needLegals = 300;
        int needPersons = 300;

        // ✅ мягкий размер страницы
        int pageSize = 15;

        int exportedLegals = 0;
        int exportedPersons = 0;

        try (ExcelExporter excel = new ExcelExporter()) {

            // ---------------- LEGALS ----------------
            int offset = 0;
            int got = 0;
            int debugDone = 0;

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

                    // 1) строим строку
                    LegalEntityRow row = legalBuilder.buildFromListItem(item);
                    excel.appendLegal(row);
                    exportedLegals++;

                    // 2) DEBUG запросы — только если включено и только для первых DEBUG_ITEMS_LIMIT
                    if (DEBUG_PER_ITEM && debugDone < DEBUG_ITEMS_LIMIT) {
                        debugDone++;

                        // candidates — опасная часть: много запросов. Поэтому только 1 элемент.
                        String[] candidates = new String[] {
                                "/backend/cmpbankrupts/" + guid + "/trades?limit=1&offset=0",
                                "/backend/cmpbankrupts/" + guid + "/publications?limit=1&offset=0"
                        };

                        for (String p : candidates) {
                            try {
                                sleepQuiet(SLEEP_MS);
                                String resp = bankrot.get(p, Map.of("Referer", "https://bankrot.fedresurs.ru/"));
                                System.out.println("OK 200: https://bankrot.fedresurs.ru" + p + " head=" + head(resp));
                            } catch (Exception e) {
                                System.out.println("FAIL: https://bankrot.fedresurs.ru" + p + " -> " + e.getMessage());
                            }
                        }

                        try {
                            sleepQuiet(SLEEP_MS);

                            // guid тут должен быть bankruptGuid из списка /backend/cmpbankrupts или /backend/prsnbankrupts
                            String bankruptGuid = guid;

                            String biddingsPath = FedresursEndpoints.biddingsByBankruptGuid(bankruptGuid, 15, 0);
                            String biddingsJson = fed.get(biddingsPath, referer("https://fedresurs.ru"));

                            System.out.println("BIDDINGS(FED) URL = https://fedresurs.ru" + biddingsPath);
                            System.out.println("BIDDINGS(FED) head = " + head(biddingsJson));

                        } catch (Exception e) {
                            System.out.println("BIDDINGS(FED) ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }

                        try {
                            sleepQuiet(SLEEP_MS);
                            String bankruptcyPath = FedresursEndpoints.companyBankruptcy(guid);
                            String bankruptcyJson = fed.get(bankruptcyPath, referer("https://fedresurs.ru"));
                            System.out.println("BANKRUPTCY URL = https://fedresurs.ru" + bankruptcyPath);
                            System.out.println("BANKRUPTCY RESP head = " + head(bankruptcyJson));
                        } catch (Exception e) {
                            System.out.println("BANKRUPTCY ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }

                        try {
                            sleepQuiet(SLEEP_MS);
                            String iebPath = FedresursEndpoints.companyIeb(guid);
                            String iebJson = fed.get(iebPath, referer("https://fedresurs.ru"));
                            System.out.println("IEB URL = https://fedresurs.ru" + iebPath);
                            System.out.println("IEB RESP head = " + head(iebJson));
                        } catch (Exception e) {
                            System.out.println("IEB ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }

                    got++;
                    if (got >= needLegals) break;
                }

                offset += pageSize;
                sleepQuiet(SLEEP_MS);
            }

            // ---------------- PERSONS ----------------
            offset = 0;
            got = 0;

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

                    System.out.println("\n--- PERSON ITEM ---");
                    System.out.println("PERSON GUID = " + guid);
                    System.out.println("PERSON caseNumber = " + caseNumber);

                    PhysicalPersonRow row = personBuilder.buildFromListItem(item);
                    excel.appendPhysical(row);
                    exportedPersons++;

                    got++;
                    if (got >= needPersons) break;
                }

                offset += pageSize;
                sleepQuiet(SLEEP_MS);
            }

            excel.saveAtomic(Path.of("fedresurs_debtors_" + System.currentTimeMillis() + ".xlsx"));
        }

        System.out.println("OK: exported " + exportedLegals + " legal + " + exportedPersons + " persons");
    }
}