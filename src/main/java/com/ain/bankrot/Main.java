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

    public static void main(String[] args) throws Exception {

        Map<String, String> headersBankrot = Map.of(
                "User-Agent", "Mozilla/5.0",
                "Accept", "application/json",
                "Referer", "https://bankrot.fedresurs.ru/",
                "Origin", "https://bankrot.fedresurs.ru"
        );

        ApiClient bankrot = new ApiClient("https://bankrot.fedresurs.ru", headersBankrot);

        ApiClient fed = new ApiClient("https://fedresurs.ru", Map.of(
                "User-Agent", "Mozilla/5.0",
                "Accept", "application/json",
                "Referer", "https://fedresurs.ru/",
                "Origin", "https://fedresurs.ru"
        ));

        ObjectMapper om = new ObjectMapper();

        LegalRowBuilder legalBuilder = new LegalRowBuilder(fed);
        PersonRowBuilder personBuilder = new PersonRowBuilder(fed);

        int need = 1;
        int pageSize = 15;

        int exportedLegals = 0;
        int exportedPersons = 0;

        try (ExcelExporter excel = new ExcelExporter()) {

            // ---------------- LEGALS ----------------
            int offset = 0;
            int got = 0;
            while (got < need) {

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


                    String[] candidates = new String[] {
                            "/backend/cmpbankrupts/" + guid + "/trades?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/trade?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/tenders?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/tender?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/auctions?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/auction?limit=1&offset=0",
                            "/backend/cmpbankrupts/" + guid + "/publications?limit=1&offset=0"
                    };

                    for (String p : candidates) {
                        try {
                            String resp = bankrot.get(p, Map.of("Referer", "https://bankrot.fedresurs.ru/"));
                            System.out.println("OK 200: https://bankrot.fedresurs.ru" + p + " head=" + head(resp));
                        } catch (Exception e) {
                            System.out.println("FAIL: https://bankrot.fedresurs.ru" + p + " -> " + e.getMessage());
                        }
                    }
                    String caseNumber = safeText(item.path("lastLegalCase").path("number"));

                    System.out.println("\n--- DEBUG LEGAL ITEM ---");
                    System.out.println("LEGAL GUID = " + guid);
                    System.out.println("LEGAL caseNumber = " + caseNumber);

                    // 1) строим строку
                    LegalEntityRow row = legalBuilder.buildFromListItem(item);
                    excel.appendLegal(row);
                    exportedLegals++;

                    // 2) DEBUG эндпоинты (чтобы видеть, что реально приходит)
                    try {
                        int tradesPageSize = 1;
                        int tradesOffset = 0;

                        String tradesPathFed = FedresursEndpoints.companyTradesFed(guid, tradesPageSize, tradesOffset);
                        String tradesJsonFed = fed.get(tradesPathFed, referer("https://fedresurs.ru"));

                        System.out.println("TRADES(FED) URL = https://fedresurs.ru" + tradesPathFed);
                        System.out.println("TRADES(FED) head = " + head(tradesJsonFed));
                    } catch (Exception e) {
                        System.out.println("TRADES(FED) ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }

                    try {
                        String bankruptcyPath = FedresursEndpoints.companyBankruptcy(guid);
                        String bankruptcyJson = fed.get(bankruptcyPath, referer("https://fedresurs.ru"));
                        System.out.println("BANKRUPTCY URL = https://fedresurs.ru" + bankruptcyPath);
                        System.out.println("BANKRUPTCY RESP head = " + head(bankruptcyJson));
                    } catch (Exception e) {
                        System.out.println("BANKRUPTCY ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }

                    try {
                        String iebPath = FedresursEndpoints.companyIeb(guid);
                        String iebJson = fed.get(iebPath, referer("https://fedresurs.ru"));
                        System.out.println("IEB URL = https://fedresurs.ru" + iebPath);
                        System.out.println("IEB RESP head = " + head(iebJson));
                    } catch (Exception e) {
                        System.out.println("IEB ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }

                    got++;
                    if (got >= need) break;
                }
                offset += pageSize;
            }

            // ---------------- PERSONS ----------------
            offset = 0;
            got = 0;
            while (got < need) {

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
                    String caseNumber = safeText(item.path("lastLegalCase").path("number")); // ✅ как в JSON

                    System.out.println("\n--- DEBUG PERSON ITEM ---");
                    System.out.println("PERSON GUID = " + guid);
                    System.out.println("PERSON caseNumber = " + caseNumber);

                    PhysicalPersonRow row = personBuilder.buildFromListItem(item);
                    excel.appendPhysical(row);
                    exportedPersons++;

                    got++;
                    if (got >= need) break;
                }
                offset += pageSize;
            }

            excel.saveAtomic(Path.of("fedresurs_debtors_" + System.currentTimeMillis() + ".xlsx"));
        }

        System.out.println("OK: exported " + exportedLegals + " legal + " + exportedPersons + " persons");
    }
}