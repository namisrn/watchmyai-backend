package com.watchmyai.common.api;

public record ApiFieldError(
        String field,
        String message
) {
}