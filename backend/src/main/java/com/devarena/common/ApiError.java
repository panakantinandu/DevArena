package com.devarena.common;

import java.time.Instant;
import java.util.Map;

/** The single error shape returned by every endpoint. */
public record ApiError(String code, String message, Map<String, String> fieldErrors, Instant timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }
}
