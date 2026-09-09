package com.ykalcheu.coffee.app.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        List<FieldErrorResponse> fieldErrors
) {
    static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, details, null);
    }

    static ApiError validation(List<FieldErrorResponse> fieldErrors) {
        return new ApiError("VALIDATION_ERROR", "Request validation failed.", null, fieldErrors);
    }
}
