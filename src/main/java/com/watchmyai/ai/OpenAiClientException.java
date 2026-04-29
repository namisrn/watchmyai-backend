package com.watchmyai.ai;

public class OpenAiClientException extends RuntimeException {

    private final int statusCode;

    public OpenAiClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public OpenAiClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 502;
    }

    public int statusCode() {
        return statusCode;
    }
}
