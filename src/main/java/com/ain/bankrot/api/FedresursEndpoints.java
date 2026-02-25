package com.ain.bankrot.api;

public final class FedresursEndpoints {
    private FedresursEndpoints() {}

    // bankrot base
    public static String listCompanies(int limit, int offset) {
        return "/backend/cmpbankrupts?isActiveLegalCase=true&limit=" + limit + "&offset=" + offset;
    }
    public static String listPersons(int limit, int offset) {
        return "/backend/prsnbankrupts?isActiveLegalCase=true&limit=" + limit + "&offset=" + offset;
    }

    // fed base
    public static String company(String guid) {
        return "/backend/companies/" + guid;
    }
    public static String companyPublications(String guid, int limit, int offset) {
        return "/backend/companies/" + guid + "/publications?limit=" + limit + "&offset=" + offset;
    }
    public static String companyTrades(String guid, int limit, int offset) {
        return "/backend/cmpbankrupts/" + guid + "/trades?limit=" + limit + "&offset=" + offset;

    }
    public static String companyTradesFed(String guid, int limit, int offset) {
        return "/backend/companies/" + guid + "/trades?limit=" + limit + "&offset=" + offset;

    }
    public static String companyBankruptcy(String guid) {
        return "/backend/companies/" + guid + "/bankruptcy";
    }
    public static String companyIeb(String guid) {
        return "/backend/companies/" + guid + "/ieb";
    }

    public static String person(String guid) {
        return "/backend/persons/" + guid;
    }
    public static String personIndividualEntrepreneurs(String guid, int limit, int offset) {
        return "/backend/persons/" + guid + "/individual-entrepreneurs?limit=" + limit + "&offset=" + offset;
    }
    public static String personGeneralInfo(String guid) {
        return "/backend/persons/" + guid + "/general-info";
    }
}
