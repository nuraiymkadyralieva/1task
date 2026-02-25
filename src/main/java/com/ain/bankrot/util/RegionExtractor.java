package com.ain.bankrot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegionExtractor {
    private RegionExtractor() {}

    private static final Pattern INDEX = Pattern.compile("^\\s*\\d{5,6}\\s*,\\s*");
    private static final Pattern FED = Pattern.compile("(Г\\.?\\s*)?(МОСКВА|САНКТ\\s*[- ]?ПЕТЕРБУРГ|СЕВАСТОПОЛЬ)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGION = Pattern.compile(
            "(РЕСПУБЛИКА\\s+[^,]+|[^,]+\\s+ОБЛАСТЬ|[^,]+\\s+КРАЙ|[^,]+\\s+АВТОНОМНЫЙ\\s+ОКРУГ|[^,]+\\s+АВТОНОМНАЯ\\s+ОБЛАСТЬ)",
            Pattern.CASE_INSENSITIVE
    );

    public static String extract(String address) {
        if (address == null) return "";
        String a = address.trim().replace('Ё','Е').replace('ё','е');
        if (a.isBlank()) return "";

        a = INDEX.matcher(a).replaceFirst("");

        Matcher mFed = FED.matcher(a);
        if (mFed.find()) return capitalize(mFed.group(2));

        Matcher m = REGION.matcher(a);
        if (m.find()) return capitalize(m.group(1).trim());

        String first = a.split(",")[0].trim();
        if (first.matches("\\d+")) return "";
        return capitalize(first);
    }

    private static String capitalize(String s) {
        String[] p = s.toLowerCase().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String x : p) {
            if (x.isBlank()) continue;
            b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)).append(" ");
        }
        return b.toString().trim().replace("Санкт-петербург", "Санкт-Петербург");
    }
}
