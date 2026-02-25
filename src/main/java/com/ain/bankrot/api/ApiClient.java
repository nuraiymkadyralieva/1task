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
                .callTimeout(Duration.ofSeconds(40))
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(40))
                .build();
    }

    public String get(String path) throws IOException {
        return get(path, Map.of());
    }

    public String get(String path, Map<String, String> headers) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(baseUrl + path)
                .get();

        // default headers
        for (var e : defaultHeaders.entrySet()) b.header(e.getKey(), e.getValue());
        // overrides
        for (var e : headers.entrySet()) b.header(e.getKey(), e.getValue());

        Request req = b.build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() == null ? "" : resp.body().string();
                throw new IOException("HTTP " + resp.code() + " GET " + path + " body=" + body);
            }
            return resp.body() == null ? "" : resp.body().string();
        }
    }
}
