package com.watchmyai.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String clientRequestId,
        List<ApiFieldError> fieldErrors
) {
}