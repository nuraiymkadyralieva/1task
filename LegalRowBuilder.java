package com.ain.bankrot.service;

import com.ain.bankrot.api.ApiClient;
import com.ain.bankrot.api.FedresursEndpoints;
import com.ain.bankrot.model.legal.LegalEntityRow;
import com.ain.bankrot.util.Dates;
import com.ain.bankrot.util.RegionExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class LegalRowBuilder {

    private final ApiClient fed;
    private final ObjectMapper om = new ObjectMapper();
    private final CompanyMapper companyMapper = new CompanyMapper();

    private static final boolean DEBUG_TRADES = true;

    public LegalRowBuilder(ApiClient fed) {
        this.fed = fed;
    }

    public LegalEntityRow buildFromListItem(JsonNode itemFromBankrotList) throws Exception {
        String guid = itemFromBankrotList.path("guid").asText("");
        LegalEntityRow row = new LegalEntityRow();
        if (guid.isBlank()) return row;

        // -----------------------------
        // 1) ДАННЫЕ ИЗ СПИСКА — lastLegalCase
        // -----------------------------
        JsonNode last = itemFromBankrotList.path("lastLegalCase");

        // ✅ ВАЖНО: список запрашивается isActiveLegalCase=true,
        // поэтому если статус дела не нашли — считаем "Активно"
        String activeHint = statusFromBooleans(itemFromBankrotList);
        if (activeHint.isBlank() && itemFromBankrotList.path("isActiveLegalCase").asBoolean(false)) {
            activeHint = "Активно";
        }

        row.caseNumber = firstNonBlank(
                last.path("number").asText(""),
                last.path("caseNumber").asText(""),
                last.path("case").path("number").asText("")
        );

        String statusName = last.path("status").path("name").asText("");
        String statusDesc = last.path("status").path("description").asText("");

        // ✅ ProcedureType: "Наблюдение / Конкурсное производство / ..."
        row.procedureType = firstNonBlank(
                extractProcedureTypeFromLastLegalCase(last),
                looksLikeProcedure(statusName) ? statusName : "",
                looksLikeProcedure(statusDesc) ? statusDesc : ""
        );

        // ✅ CaseStatus: "Активно/Завершено/..."
        row.caseStatus = firstNonBlank(
                activeHint,
                statusFromBooleans(itemFromBankrotList),
                statusFromBooleans(last),
                last.path("caseStatus").asText(""),
                last.path("statusName").asText(""),
                looksLikeCaseStatus(statusDesc) ? statusDesc : "",
                looksLikeCaseStatus(statusName) ? statusName : ""
        );

        row.arbitrationManagerName = firstNonBlank(
                last.path("arbitrManagerFio").asText(""),
                last.path("arbitrationManager").path("name").asText(""),
                last.path("arbitrManager").path("name").asText(""),
                last.path("manager").path("name").asText("")
        );

        row.arbitrationManagerInn = firstNonBlank(
                last.path("arbitrManagerInn").asText(""),
                last.path("arbitrManagerINN").asText(""),
                last.path("arbitrationManager").path("inn").asText(""),
                last.path("manager").path("inn").asText("")
        );

        row.managerAppointmentDate = Dates.toDdMmYyyyFromIsoDateTime(firstNonBlank(
                last.path("managerAppointmentDate").asText(""),
                last.path("appointmentDate").asText(""),
                last.path("arbitrManagerDate").asText(""),
                last.path("arbitrManagerSince").asText("")
        ));

        // ✅ CaseEndDate — попытка №1: из lastLegalCase
        String endFromLast = firstNonBlank(
                last.path("caseEndDate").asText(""),
                last.path("endDate").asText(""),
                last.path("dateEnd").asText(""),
                last.path("finishDate").asText(""),
                last.path("completionDate").asText(""),
                last.path("dateFinish").asText(""),
                last.path("dateCompletion").asText("")
        );
        row.caseEndDate = Dates.toDdMmYyyyFromIsoDateTime(endFromLast);

        row.region = firstNonBlank(
                itemFromBankrotList.path("region").path("name").asText(""),
                itemFromBankrotList.path("region").asText("")
        );

        // -----------------------------
        // 2) КАРТОЧКА КОМПАНИИ (fedresurs.ru)
        // -----------------------------
        String companyPath = FedresursEndpoints.company(guid);
        String companyJson = fed.get(companyPath, refererFed());
        LegalEntityRow base = companyMapper.fromCompanyJson(companyJson, "https://fedresurs.ru" + companyPath);

        mergeLegal(row, base);

        if (row.region.isBlank()) row.region = RegionExtractor.extract(row.address);

        // -----------------------------
        // 3) PublicationsCount
        // -----------------------------
        row.publicationsCount = readCountSafe(FedresursEndpoints.companyPublications(guid, 1, 0), false);

        // -----------------------------
        // 4) TradesCount: цифра или "н/д"
        // -----------------------------
        String trades = readCountSafe(FedresursEndpoints.companyTrades(guid, 1, 0), true);
        row.tradesCount = trades.isBlank() ? "н/д" : trades;

        // -----------------------------
        // 5) BANKRUPTCY DETAILS → CaseEndDate (попытка №2) + статус из boolean
        // -----------------------------
        fillFromBankruptcy(row, guid);

        // ✅ CaseEndDate: если совсем не нашли — ставим н/д
        if (row.caseEndDate == null || row.caseEndDate.isBlank()) {
            row.caseEndDate = "н/д";
        }

        // ✅ CaseStatus: если всё равно пустой — ставим "Активно"
        if (row.caseStatus == null || row.caseStatus.isBlank()) {
            row.caseStatus = "Активно";
        }

        // -----------------------------
        // 6) IEB → INN управляющего + дата внесения (не затираем)
        // -----------------------------
        fillFromIeb(row, guid);

        // DEBUG
        // System.out.println("DEBUG CaseStatus=[" + row.caseStatus + "] ProcedureType=[" + row.procedureType + "]");

        return row;
    }

    // =========================================================
    // Bankruptcy block
    // =========================================================
    private void fillFromBankruptcy(LegalEntityRow row, String guid) {
        try {
            String bjson = fed.get(FedresursEndpoints.companyBankruptcy(guid), refererFed());
            JsonNode broot = om.readTree(bjson);

            // CaseEndDate из /bankruptcy, если ещё нет
            if (row.caseEndDate == null || row.caseEndDate.isBlank() || "н/д".equals(row.caseEndDate)) {
                String endIso = firstNonBlank(
                        findDeep(broot, "caseEndDate", "endDate", "dateEnd", "finishDate", "completionDate", "dateFinish", "dateCompletion")
                );
                if (!endIso.isBlank()) {
                    row.caseEndDate = Dates.toDdMmYyyyFromIsoDateTime(endIso);
                } else {
                    JsonNode firstCase = firstLegalCase(broot);
                    if (firstCase != null) {
                        String endIso2 = firstNonBlank(
                                firstCase.path("caseEndDate").asText(""),
                                firstCase.path("endDate").asText(""),
                                firstCase.path("dateEnd").asText(""),
                                firstCase.path("finishDate").asText(""),
                                firstCase.path("completionDate").asText("")
                        );
                        if (!endIso2.isBlank()) {
                            row.caseEndDate = Dates.toDdMmYyyyFromIsoDateTime(endIso2);
                        }
                    }
                }
            }

            // если CaseStatus пустой — пробуем boolean из /bankruptcy
            if (row.caseStatus == null || row.caseStatus.isBlank()) {
                String s = statusFromBooleans(broot);
                if (!s.isBlank()) row.caseStatus = s;
            }

        } catch (Exception ignore) {}
    }

    private static JsonNode firstLegalCase(JsonNode broot) {
        JsonNode arr = broot.path("legalCases");
        if (arr.isArray() && arr.size() > 0) return arr.get(0);
        return null;
    }

    // =========================================================
    // IEB block
    // =========================================================
    private void fillFromIeb(LegalEntityRow row, String guid) {
        try {
            String ij = fed.get(FedresursEndpoints.companyIeb(guid), refererFed());
            JsonNode ir = om.readTree(ij);

            JsonNode pd0 = null;
            JsonNode pd = ir.path("pageData");
            if (pd.isArray() && pd.size() > 0) pd0 = pd.get(0);

            String inn = firstNonBlank(
                    findDeep(ir, "arbitrationManagerInn", "arbitrationManagerINN", "arbitrManagerInn", "managerInn"),
                    ir.path("arbitrationManager").path("inn").asText(""),
                    ir.path("manager").path("inn").asText(""),
                    ir.path("inn").asText(""),
                    pd0 != null ? pd0.path("inn").asText("") : "",
                    pd0 != null ? pd0.path("arbitrationManagerInn").asText("") : "",
                    pd0 != null ? pd0.path("arbitrationManager").path("inn").asText("") : ""
            );
            if (!inn.isBlank() && row.arbitrationManagerInn.isBlank()) {
                row.arbitrationManagerInn = inn;
            }

            String appIso = firstNonBlank(
                    findDeep(ir, "managerAppointmentDate", "appointmentDate", "arbitrManagerDate", "arbitrManagerSince"),
                    findDeep(ir, "egrulDateCreate", "dateCreate", "dateCreated"),
                    ir.path("date").asText(""),
                    pd0 != null ? pd0.path("managerAppointmentDate").asText("") : "",
                    pd0 != null ? pd0.path("appointmentDate").asText("") : "",
                    pd0 != null ? pd0.path("egrulDateCreate").asText("") : "",
                    pd0 != null ? pd0.path("dateCreate").asText("") : "",
                    pd0 != null ? pd0.path("date").asText("") : ""
            );

            String app = Dates.toDdMmYyyyFromIsoDateTime(appIso);
            if (!app.isBlank() && row.managerAppointmentDate.isBlank()) {
                row.managerAppointmentDate = app;
            }

        } catch (Exception ignore) {}
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String readCountSafe(String path, boolean isTrades) {
        try {
            String j = fed.get(path, refererFed());

            if (isTrades && DEBUG_TRADES) {
                System.out.println("TRADES URL = https://fedresurs.ru" + path);
                System.out.println("TRADES RESP head = " + j.substring(0, Math.min(160, j.length())));
            }

            JsonNode root = om.readTree(j);

            int found = root.path("found").asInt(-1);
            if (found >= 0) return String.valueOf(found);

            int total = root.path("total").asInt(-1);
            if (total >= 0) return String.valueOf(total);

            int count = root.path("count").asInt(-1);
            if (count >= 0) return String.valueOf(count);

            JsonNode pd = root.path("pageData");
            if (pd.isArray()) return String.valueOf(pd.size());

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractProcedureTypeFromLastLegalCase(JsonNode last) {
        if (last == null || last.isMissingNode() || last.isNull()) return "";

        String p1 = last.path("procedure").path("name").asText("");
        if (!p1.isBlank()) return p1;

        String p1b = last.path("procedure").path("description").asText("");
        if (!p1b.isBlank()) return p1b;

        String p1c = last.path("procedure").path("type").asText("");
        if (!p1c.isBlank()) return p1c;

        String p2 = last.path("procedure").asText("");
        if (!p2.isBlank()) return p2;

        String p3 = last.path("procedureType").asText("");
        if (!p3.isBlank()) return p3;

        String p4 = last.path("procedureName").asText("");
        if (!p4.isBlank()) return p4;

        String p5 = last.path("type").asText("");
        if (!p5.isBlank()) return p5;

        return "";
    }

    private static void mergeLegal(LegalEntityRow target, LegalEntityRow base) {
        target.fullName = base.fullName;
        target.inn = base.inn;
        target.ogrn = base.ogrn;
        target.kpp = base.kpp;
        target.authorizedCapital = base.authorizedCapital;
        target.registrationDate = base.registrationDate;
        target.address = base.address;
        if (target.region.isBlank()) target.region = base.region;
        target.legalForm = base.legalForm;
        target.okved = base.okved;
        target.status = base.status;
        target.sourceUrl = base.sourceUrl;
    }

    private static Map<String, String> refererFed() {
        return Map.of("Referer", "https://fedresurs.ru/");
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) if (x != null && !x.isBlank()) return x.trim();
        return "";
    }

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

                String name = direct.path("name").asText("");
                if (!name.isBlank()) return name;

                String desc = direct.path("description").asText("");
                if (!desc.isBlank()) return desc;
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

    // ========= статус из boolean (isActive / isFinished / isClosed ...) =========
    private static String statusFromBooleans(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";

        // ✅ активность дела часто обозначается так:
        Boolean active = findDeepBoolean(node,
                "isActive", "active",
                "isActiveLegalCase", "activeLegalCase",
                "isActiveCase", "activeCase"
        );

        // ✅ завершение тоже бывает под разными ключами
        Boolean finished = findDeepBoolean(node,
                "isFinished", "finished",
                "isEnded", "ended",
                "isClosed", "closed",
                "isCompleted", "completed",
                "isTerminated", "terminated"
        );

        if (Boolean.TRUE.equals(finished)) return "Завершено";
        if (Boolean.TRUE.equals(active)) return "Активно";
        if (Boolean.FALSE.equals(active)) return "Завершено";

        return "";
    }

    private static Boolean findDeepBoolean(JsonNode node, String... keys) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;

        if (node.isObject()) {
            for (String k : keys) {
                JsonNode v = node.get(k);
                if (v != null && !v.isNull()) {
                    if (v.isBoolean()) return v.asBoolean();
                    if (v.isInt() || v.isLong()) {
                        int n = v.asInt();
                        if (n == 0) return false;
                        if (n == 1) return true;
                    }
                    if (v.isTextual()) {
                        String t = v.asText("").trim().toLowerCase();
                        if (t.equals("true")) return true;
                        if (t.equals("false")) return false;
                        if (t.equals("1")) return true;
                        if (t.equals("0")) return false;
                    }
                }
            }
            var it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                Boolean got = findDeepBoolean(e.getValue(), keys);
                if (got != null) return got;
            }
        } else if (node.isArray()) {
            for (JsonNode x : node) {
                Boolean got = findDeepBoolean(x, keys);
                if (got != null) return got;
            }
        }

        return null;
    }

    // эвристика: похоже ли на статус дела ("Активно/Завершено/...")
    private static boolean looksLikeCaseStatus(String s) {
        if (s == null) return false;
        String x = s.trim().toLowerCase();
        return x.contains("актив") || x.contains("заверш") || x.contains("прекращ")
                || x.contains("оконч") || x.contains("закрыт") || x.contains("введен")
                || x.contains("введён");
    }

    // эвристика: похоже ли на процедуру ("Наблюдение/Конкурсное/...")
    private static boolean looksLikeProcedure(String s) {
        if (s == null) return false;
        String x = s.trim().toLowerCase();
        return x.contains("наблюден") || x.contains("конкурс") || x.contains("реструкт")
                || x.contains("реализац") || x.contains("оздоров") || x.contains("управлен")
                || x.contains("мировое") || x.contains("производств");
    }
}