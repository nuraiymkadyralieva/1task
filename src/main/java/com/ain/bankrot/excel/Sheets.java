package com.ain.bankrot.excel;

import java.util.List;

public final class Sheets {

    private Sheets() {}

    public static final String LEGAL = "LegalEntities";
    public static final String PHYSICAL = "PhysicalPersons";

    public static final List<String> LEGAL_HEADERS = List.of(
            "FullName","INN","OGRN","KPP","AuthorizedCapital","RegistrationDate","Address","Region",
            "LegalForm","OKVED","Status","ProcedureType","CaseNumber","CaseStatus","CaseEndDate",
            "ArbitrationManagerName","ArbitrationManagerINN","ManagerAppointmentDate",
            "PublicationsCount","TradesCount","SourceURL"
    );

    public static final List<String> PHYSICAL_HEADERS = List.of(
            "FullName","PreviousFullName","INN","SNILS","BirthDate","BirthPlace","ResidenceAddress","Region",
            "EntrepreneurOGRNIP","EntrepreneurStatus","OKVED","RegistrationDate","TerminationDate",
            "BankruptcyStatus","ProcedureType","CaseNumber","ArbitrationManagerName","SourceURL"
    );
}
