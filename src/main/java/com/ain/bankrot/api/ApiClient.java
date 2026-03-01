package com.ain.bankrot.api;

import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class ApiClient {
    private final OkHttpClient client;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;

    public ApiClient(String baseUrl, Map<String, String> defaultHeaders) {
        this.baseUrl = baseUrl;
        this.defaultHeaders = defaultHeaders;

        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(45))
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(45))
                .build();
    }

    public String get(String path) throws IOException {
        return get(path, Map.of());
    }

    /**
     * GET с retry для антибота/лимитов.
     * ВАЖНО: при блоке (451/429/403) и после нескольких попыток возвращает "" (не падает),
     * чтобы парсер продолжал работу и хотя бы частично выгрузил Excel.
     */
    public String get(String path, Map<String, String> headers) throws IOException {
        int maxAttempts = 5;
        long backoffMs = 1200;

        IOException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Request req = buildRequest(path, headers);

            try (Response resp = client.newCall(req).execute()) {

                int code = resp.code();
                String body = resp.body() == null ? "" : resp.body().string();

                if (code >= 200 && code < 300) {
                    return body;
                }

                // retry codes
                if (code == 451 || code == 429 || code == 403 || code == 502 || code == 503 || code == 504) {
                    System.out.println("HTTP " + code + " GET " + path +
                            " attempt=" + attempt + "/" + maxAttempts +
                            " -> backoff " + backoffMs + "ms; bodyHead=" + head(body, 200));

                    sleepQuiet(backoffMs);
                    backoffMs = Math.min((long) (backoffMs * 1.8), 15000);
                    continue;
                }

                // остальные коды: не ретраим, но и не падаем — просто вернем пусто
                System.out.println("HTTP " + code + " GET " + path +
                        " (no retry) bodyHead=" + head(body, 200));
                return "";

            } catch (IOException e) {
                last = e;
                // сетевые ошибки тоже иногда временные — ретраим
                System.out.println("IO ERROR GET " + path + " attempt=" + attempt + "/" + maxAttempts +
                        " -> " + e.getClass().getSimpleName() + ": " + e.getMessage() +
                        " ; backoff " + backoffMs + "ms");

                sleepQuiet(backoffMs);
                backoffMs = Math.min((long) (backoffMs * 1.8), 15000);
            }
        }

        // если после всех попыток всё равно плохо — НЕ роняем весь прогон
        System.out.println("BLOCKED/FAILED (max retries) GET " + path + " -> return empty");
        if (last != null) {
            // если хочешь — можешь временно раскомментить, чтобы видеть последнюю ошибку
            // throw last;
        }
        return "";
    }

    private Request buildRequest(String path, Map<String, String> headers) {
        Request.Builder b = new Request.Builder()
                .url(baseUrl + path)
                .get();

        // default headers
        if (defaultHeaders != null) {
            for (var e : defaultHeaders.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) b.header(e.getKey(), e.getValue());
            }
        }

        // overrides
        if (headers != null) {
            for (var e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) b.header(e.getKey(), e.getValue());
            }
        }

        // базовые "человеческие" заголовки (не мешают, иногда помогают против 451/403)
        if (!hasHeader(defaultHeaders, headers, "User-Agent")) {
            b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        }
        if (!hasHeader(defaultHeaders, headers, "Accept")) {
            b.header("Accept", "application/json, text/plain, */*");
        }
        if (!hasHeader(defaultHeaders, headers, "Accept-Language")) {
            b.header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
        }
        if (!hasHeader(defaultHeaders, headers, "Connection")) {
            b.header("Connection", "keep-alive");
        }

        return b.build();
    }

    private static boolean hasHeader(Map<String, String> defaults, Map<String, String> overrides, String key) {
        if (overrides != null) {
            for (var e : overrides.entrySet()) {
                if (key.equalsIgnoreCase(e.getKey())) return true;
            }
        }
        if (defaults != null) {
            for (var e : defaults.entrySet()) {
                if (key.equalsIgnoreCase(e.getKey())) return true;
            }
        }
        return false;
    }

    private static String head(String s, int n) {
        if (s == null) return "";
        return s.substring(0, Math.min(n, s.length())).replaceAll("\\s+", " ").trim();
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}