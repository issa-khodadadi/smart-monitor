package com.issa.smartmonitor.web;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.Map;

/**
 * One envelope for every monitor API. The dashboard renders {@code schema} + {@code data}
 * and does not assume a private shape per endpoint.
 */
@Getter
public class ApiView {

    private final boolean ok;
    private final String code;
    private final String view;
    private final long generatedAt;
    private final String message;
    private final Map<String, Object> meta;
    private final JsonNode schema;
    private final Object data;

    public ApiView(boolean ok, String code, String view, long generatedAt, String message,
                   Map<String, Object> meta, JsonNode schema, Object data) {
        this.ok = ok;
        this.code = code;
        this.view = view;
        this.generatedAt = generatedAt;
        this.message = message;
        this.meta = meta;
        this.schema = schema;
        this.data = data;
    }

    public static ApiView of(boolean ok, String code, String view, String message,
                             Map<String, Object> meta, JsonNode schema, Object data) {
        return new ApiView(ok, code, view, System.currentTimeMillis(), message, meta, schema, data);
    }
}
