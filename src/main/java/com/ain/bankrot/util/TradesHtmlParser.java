package com.ain.bankrot.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TradesHtmlParser {

    /**
     * Возвращает количество торгов, которые видны в карточке компании на fedresurs.ru.
     * Работает по HTML (как на твоём скрине).
     */
    public static String fetchTradesCount(String companyGuid) {
        try {
            String url = "https://fedresurs.ru/companies/" + companyGuid;

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(15_000)
                    .get();

            // 1) Находим блок, который содержит заголовок "Торги"
            Element tradesBlock = findSectionByTitle(doc, "Торги");
            if (tradesBlock == null) return "0";

            // 2) Внутри блока ищем ссылки на торги.
            // На скрине торги выглядят как кликабельные номера ("148546-МЭТС-2" и т.п.)
            // Обычно это <a> элементы.
            Elements links = tradesBlock.select("a");

            // 3) Фильтруем только те ссылки, которые похожи на номер торгов:
            // содержит "МЭТС" или "ЭТП" или похожие маркеры.
            int count = 0;
            for (Element a : links) {
                String t = a.text().trim().toUpperCase();

                // эвристика: номера торгов часто содержат "МЭТС" / "ЭТП" / "LOT" / "№"
                if (t.contains("МЭТС") || t.contains("ЭТП") || t.contains("LOT") || t.contains("№")) {
                    count++;
                }
            }

            // Если эвристика дала 0, но блок есть — можно fallback:
            // иногда ссылки без "МЭТС", тогда считаем просто количество строк/элементов списка.
            if (count == 0) {
                // частая разметка: list items / rows
                Elements rows = tradesBlock.select("li, .row, .table-row");
                if (rows.size() > 0) return String.valueOf(rows.size());
            }

            return String.valueOf(count);

        } catch (Exception e) {
            // если сайт временно недоступен/капча — не валим весь экспорт
            return "";
        }
    }

    /**
     * Ищет секцию по заголовку (например "Торги") и возвращает ближайший "контейнер" секции.
     * Это эвристика, но на практике работает хорошо для таких страниц.
     */
    private static Element findSectionByTitle(Document doc, String title) {
        // ищем любой элемент, который содержит текст заголовка
        // и поднимаемся вверх к блоку-карточке
        for (Element el : doc.getAllElements()) {
            if (title.equalsIgnoreCase(el.text().trim())) {
                // поднимаемся к ближайшему контейнеру, который выглядит как карточка/секция
                Element cur = el;
                for (int i = 0; i < 6 && cur != null; i++) {
                    if (looksLikeSectionContainer(cur)) return cur;
                    cur = cur.parent();
                }
            }
        }

        // запасной поиск: элемент, содержащий "Торги" внутри заголовка
        Elements headers = doc.select("h1,h2,h3,h4,div,span");
        for (Element h : headers) {
            if (h.text() != null && h.text().trim().equalsIgnoreCase(title)) {
                Element cur = h;
                for (int i = 0; i < 6 && cur != null; i++) {
                    if (looksLikeSectionContainer(cur)) return cur;
                    cur = cur.parent();
                }
            }
        }

        return null;
    }

    private static boolean looksLikeSectionContainer(Element el) {
        String cls = el.className() == null ? "" : el.className().toLowerCase();
        // подстраховка: часто секции сидят в "card", "section", "block" и т.п.
        return cls.contains("card") || cls.contains("section") || cls.contains("block") || el.tagName().equals("section");
    }
}