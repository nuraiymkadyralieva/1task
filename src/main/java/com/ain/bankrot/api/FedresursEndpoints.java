package com.ain.bankrot.api;

public final class FedresursEndpoints {
    private FedresursEndpoints() {}

    // =========================
    // BANKROT LISTS (bankrot.fedresurs.ru backend)
    // =========================

    public static String listCompanies(int limit, int offset) {
        return "/backend/cmpbankrupts?isActiveLegalCase=true&limit=" + limit + "&offset=" + offset;
    }

    public static String listPersons(int limit, int offset) {
        return "/backend/prsnbankrupts?isActiveLegalCase=true&limit=" + limit + "&offset=" + offset;
    }

    // =========================
    // FEDRESURS CARDS (fedresurs.ru backend)
    // =========================

    public static String company(String guid) {
        return "/backend/companies/" + guid;
    }

    public static String person(String guid) {
        return "/backend/persons/" + guid;
    }

    // =========================
    // PUBLICATIONS (fedresurs.ru backend)
    // =========================

    public static String companyPublications(String companyGuid, int limit, int offset) {
        return "/backend/companies/" + companyGuid + "/publications?limit=" + limit + "&offset=" + offset;
    }

    // =========================
    // BIDDINGS / TRADES (fedresurs.ru backend)
    // Торги НЕ живут в /cmpbankrupts/{guid}/trades. Они живут в /backend/biddings?bankruptGuid=...
    // =========================

    /**
     * Список торгов по банкроту (bankruptGuid берется из списка /backend/cmpbankrupts или /backend/prsnbankrupts).
     * Пример:
     * /backend/biddings?limit=15&offset=0&bankruptGuid=e59649c2-...
     */
    public static String biddingsByBankruptGuid(String bankruptGuid, int limit, int offset) {
        return "/backend/biddings?limit=" + limit + "&offset=" + offset + "&bankruptGuid=" + bankruptGuid;
    }

    /** Детали торгов */
    public static String bidding(String biddingGuid) {
        return "/backend/biddings/" + biddingGuid;
    }

    /** Лоты торгов */
    public static String biddingLots(String biddingGuid, int limit, int offset) {
        return "/backend/biddings/" + biddingGuid + "/lots?limit=" + limit + "&offset=" + offset;
    }

    // =========================
    // OPTIONAL (оставляем только если реально используешь в коде)
    // =========================

    public static String companyBankruptcy(String companyGuid) {
        return "/backend/companies/" + companyGuid + "/bankruptcy";
    }

    public static String companyIeb(String companyGuid) {
        return "/backend/companies/" + companyGuid + "/ieb";
    }

    public static String personGeneralInfo(String personGuid) {
        return "/backend/persons/" + personGuid + "/general-info";
    }

    public static String personIndividualEntrepreneurs(String personGuid, int limit, int offset) {
        return "/backend/persons/" + personGuid + "/individual-entrepreneurs?limit=" + limit + "&offset=" + offset;
    }
}