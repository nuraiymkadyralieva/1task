package com.ain.bankrot.service;

import com.ain.bankrot.api.ApiClient;
import com.ain.bankrot.api.FedresursEndpoints;
import com.ain.bankrot.model.physical.PhysicalPersonRow;
import com.ain.bankrot.util.Dates;
import com.ain.bankrot.util.RegionExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class PersonRowBuilder {

    private final ApiClient fed;
    private final ObjectMapper om = new ObjectMapper();
    private final PersonMapper personMapper = new PersonMapper();

    public PersonRowBuilder(ApiClient fed) {
        this.fed = fed;
    }

    /**
     * itemFromBankrotList — элемент из списка банкротов (bankrot.fedresurs.ru/backend/...),
     * где есть guid и lastLegalCase.
     */
    public PhysicalPersonRow buildFromListItem(JsonNode itemFromBankrotList) throws Exception {
        String guid = itemFromBankrotList.path("guid").asText("");
        PhysicalPersonRow row = new PhysicalPersonRow();
        if (guid.isBlank()) return row;

        // -----------------------------
        // 1) Деловые поля из списка (lastLegalCase)
        // -----------------------------
        JsonNode last = itemFromBankrotList.path("lastLegalCase");

        row.caseNumber = firstNonBlank(
                last.path("number").asText(""),
                last.path("caseNumber").asText(""),
                last.path("case").path("number").asText("")
        );

        row.bankruptcyStatus = firstNonBlank(
                last.path("status").path("description").asText(""),
                last.path("status").path("name").asText(""),
                last.path("statusName").asText(""),
                last.path("status").asText("")
        );

        // ProcedureType — логика как у юрлиц
        String statusName = last.path("status").path("name").asText("");
        String statusDesc = last.path("status").path("description").asText("");

        row.procedureType = firstNonBlank(
                last.path("procedure").path("name").asText(""),
                last.path("procedure").path("description").asText(""),
                last.path("procedure").path("type").asText(""),
                last.path("procedure").asText(""),
                last.path("procedureType").asText(""),
                last.path("procedureName").asText(""),
                last.path("type").asText(""),
                looksLikeProcedure(statusName) ? statusName : "",
                looksLikeProcedure(statusDesc) ? statusDesc : ""
        );

        row.arbitrationManagerName = firstNonBlank(
                last.path("arbitrManagerFio").asText(""),
                last.path("arbitrationManager").path("name").asText(""),
                last.path("arbitrManager").path("name").asText(""),
                last.path("manager").path("name").asText("")
        );

        row.region = firstNonBlank(
                itemFromBankrotList.path("region").path("name").asText(""),
                itemFromBankrotList.path("region").asText("")
        );

        // -----------------------------
        // 2) Карточка физлица (fedresurs.ru/backend/persons/{guid})
        // -----------------------------
        String personPath = FedresursEndpoints.person(guid);
        String personJson = fed.get(personPath, refererFed());
        PhysicalPersonRow base = personMapper.fromPersonJson(personJson, "https://fedresurs.ru" + personPath);

        mergePhysical(row, base);

        // FullName fallback
        if (row.fullName == null || row.fullName.isBlank()) {
            row.fullName = firstNonBlank(
                    itemFromBankrotList.path("fullName").asText(""),
                    itemFromBankrotList.path("fio").asText(""),
                    itemFromBankrotList.path("name").asText(""),
                    itemFromBankrotList.path("debtor").path("fullName").asText(""),
                    itemFromBankrotList.path("debtor").path("fio").asText(""),
                    itemFromBankrotList.path("debtor").path("name").asText("")
            );
        }

        if (row.region == null || row.region.isBlank()) {
            row.region = RegionExtractor.extract(row.residenceAddress);
        }

        // -----------------------------
        // 3) ИП-блок: OGRNIP / статус / ОКВЭД / даты
        // -----------------------------
        fillFromIndividualEntrepreneurs(row, guid);

        // -----------------------------
        // 4) PreviousFullName fallback: /general-info (если пусто)
        // -----------------------------
        if (row.previousFullName == null || row.previousFullName.isBlank()) {
            try {
                String gj = fed.get(FedresursEndpoints.personGeneralInfo(guid), refererFed());
                JsonNode gr = om.readTree(gj);

                String prev = firstNonBlank(
                        findDeep(gr, "previousFullName", "previousName", "oldName"),
                        findDeep(gr, "fioPrevious", "fullNamePrevious")
                );

                if (!prev.isBlank() && (row.fullName == null || !prev.equalsIgnoreCase(row.fullName))) {
                    row.previousFullName = prev;
                }
            } catch (Exception ignore) {}
        }

        // ВАЖНО: ничего не подставляем. Если нет — остаётся пустым.
        return row;
    }

    // =========================================================
    // entrepreneur block (individual-entrepreneurs)
    // =========================================================
    private void fillFromIndividualEntrepreneurs(PhysicalPersonRow row, String guid) {
        try {
            String ej = fed.get(FedresursEndpoints.personIndividualEntrepreneurs(guid, 1, 0), refererFed());
            JsonNode er = om.readTree(ej);

            JsonNode pd = er.path("pageData");
            if (!pd.isArray() || pd.size() == 0) return;

            JsonNode e0 = pd.get(0);

            // 1) OGRNIP
            String ogrnip = firstNonBlank(
                    e0.path("ogrnip").asText(""),
                    e0.path("ogrnIp").asText(""),
                    e0.path("ogrnipNumber").asText("")
            );
            if (!ogrnip.isBlank() && (row.entrepreneurOgrnip == null || row.entrepreneurOgrnip.isBlank())) {
                row.entrepreneurOgrnip = ogrnip;
            }

            // 2) OKVED
            String okved = joinCodeName(
                    firstNonBlank(e0.path("okved").path("code").asText(""), e0.path("okvedCode").asText("")),
                    firstNonBlank(e0.path("okved").path("name").asText(""), e0.path("okvedName").asText(""))
            );
            if (okved.isBlank()) okved = firstNonBlank(e0.path("okved").asText(""), e0.path("okvedMain").asText(""));
            if (!okved.isBlank() && (row.okved == null || row.okved.isBlank())) {
                row.okved = okved;
            }

            // 3) RegistrationDate
            String dateRegIso = firstNonBlank(
                    e0.path("dateReg").asText(""),
                    e0.path("registrationDate").asText(""),
                    e0.path("dateRegistration").asText("")
            );
            String reg = Dates.toDdMmYyyyFromIsoDateTime(dateRegIso);
            if (!reg.isBlank() && (row.registrationDate == null || row.registrationDate.isBlank())) {
                row.registrationDate = reg;
            }

            // 4) Status + TerminationDate
            JsonNode st = e0.path("status");
            boolean isActive = st.path("isActive").asBoolean(true);

            String statusName = firstNonBlank(
                    st.path("name").asText(""),
                    st.path("description").asText(""),
                    st.path("code").asText("")
            );

            String shortStatus = isActive ? "Действует" : "Прекратил деятельность";
            String finalStatus = statusName.isBlank() ? shortStatus : statusName;

            if (!finalStatus.isBlank() && (row.entrepreneurStatus == null || row.entrepreneurStatus.isBlank())) {
                row.entrepreneurStatus = finalStatus;
            }

            if (!isActive && (row.terminationDate == null || row.terminationDate.isBlank())) {
                String termIso = firstNonBlank(
                        st.path("date").asText(""),
                        e0.path("dateEnd").asText(""),
                        e0.path("terminationDate").asText("")
                );
                String term = Dates.toDdMmYyyyFromIsoDateTime(termIso);
                if (!term.isBlank()) row.terminationDate = term;
            }

        } catch (Exception ignore) {}
    }

    // =========================================================
    // merge helpers
    // =========================================================
    private static void mergePhysical(PhysicalPersonRow target, PhysicalPersonRow base) {
        target.fullName = base.fullName;
        target.previousFullName = base.previousFullName;
        target.inn = base.inn;
        target.snils = base.snils;
        target.birthDate = base.birthDate;
        target.birthPlace = base.birthPlace;
        target.residenceAddress = base.residenceAddress;
        if (target.region == null || target.region.isBlank()) target.region = base.region;
        target.sourceUrl = base.sourceUrl;
    }

    private static Map<String, String> refererFed() {
        return Map.of("Referer", "https://fedresurs.ru/");
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

    // =========================================================
    // deep-find
    // =========================================================
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

    private static boolean looksLikeProcedure(String s) {
        if (s == null) return false;
        String x = s.trim().toLowerCase();
        return x.contains("наблюден") || x.contains("конкурс") || x.contains("реструкт")
                || x.contains("реализац") || x.contains("оздоров") || x.contains("управлен")
                || x.contains("мировое") || x.contains("производств");
    }
}