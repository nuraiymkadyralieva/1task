# Fedresurs Bankrot Parser (Java → Excel)

Проект выгружает данные о банкротствах с Fedresurs (API) и формирует Excel-файл(ы) с таблицами:
- **Юридические лица** (Companies / Legal)
- **Физические лица** (Persons / Physical)

Данные собираются из нескольких endpoint’ов Fedresurs и сводятся в строки Excel через `RowBuilder`-ы.

---

## 1) Стек и зависимости

- Java (рекомендуется **17+**)
- Maven
- OkHttp (HTTP-клиент)
- Jackson (JSON)
- Apache POI (Excel)

---

## 2) Структура проекта

Ключевые пакеты:

- `com.ain.bankrot`
    - `Main` — точка входа, запускает сбор и экспорт
- `com.ain.bankrot.api`
    - `ApiClient` — HTTP GET к Fedresurs
    - `FedresursEndpoints` — генерация URL путей эндпоинтов
- `com.ain.bankrot.service`
    - `LegalRowBuilder` — собирает строку для юр. лица
    - `PersonRowBuilder` — собирает строку для физ. лица
    - `CompanyMapper` / `PersonMapper` — маппинг JSON → модель
- `com.ain.bankrot.model`
    - `legal/LegalEntityRow` — модель строки юр.лица
    - `physical/PhysicalPersonRow` — модель строки физ.лица
- `com.ain.bankrot.excel`
    - `ExcelExporter` — формирует `.xlsx`
    - `Sheets` — имена/настройки листов
- `com.ain.bankrot.util`
    - `Dates` — преобразование дат
    - `RegionExtractor` — определение региона по адресу (если нужно)

---

## 3) Логика выгрузки (в двух словах)

### 3.1 Юридические лица (Legal)
1) Берём список активных дел:
- `/backend/cmpbankrupts?isActiveLegalCase=true&limit=...&offset=...`

2) Для каждого элемента списка:
- берём `lastLegalCase` (номер дела, статус, управляющий и т.д.)
- определяем `companyGuid` (иногда отличается от `bankruptGuid`)
- получаем карточку компании:
    - `/backend/companies/{companyGuid}`
- считаем количество публикаций:
    - `/backend/companies/{companyGuid}/publications?limit=1&offset=0`
- считаем количество торгов:
    - `/backend/biddings?bankruptGuid={bankruptGuid}&limit=1&offset=0`
- дополнительно (если нужно) добираем данные из:
    - `/backend/companies/{companyGuid}/bankruptcy`
    - `/backend/companies/{companyGuid}/ieb`

### 3.2 Физические лица (Persons)
Аналогично:
1) список активных дел:
- `/backend/prsnbankrupts?isActiveLegalCase=true&limit=...&offset=...`

2) карточка персоны и доп. данные берутся из соответствующих endpoint’ов проекта.

---

## 4) Настройка окружения

### 4.1 Требования
- Java 17+
- Maven 3.8+

Проверка:
```bash
java -version
mvn -version