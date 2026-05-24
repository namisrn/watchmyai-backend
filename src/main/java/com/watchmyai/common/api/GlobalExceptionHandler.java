package com.watchmyai.common.api;

import com.watchmyai.ai.AiJobNotFoundException;
import com.watchmyai.ai.OpenAiClientException;
import com.watchmyai.user.AuthenticationRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BAD_REQUEST_ERROR = "Bad Request";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toApiFieldError)
                .toList();

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                BAD_REQUEST_ERROR,
                "Request validation failed.",
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                BAD_REQUEST_ERROR,
                "Malformed JSON request.",
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                BAD_REQUEST_ERROR,
                exception.getMessage(),
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(OpenAiClientException.class)
    public ResponseEntity<ApiErrorResponse> handleOpenAiError(
            OpenAiClientException exception,
            HttpServletRequest request
    ) {
        String userSafeMessage = switch (exception.statusCode()) {
            case 401, 403 -> "AI provider authentication failed.";
            case 429 -> "AI provider rate limit reached. Please try again shortly.";
            case 500, 502, 503, 504 -> "AI provider is temporarily unavailable.";
            default -> "AI provider request failed.";
        };

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                userSafeMessage,
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(AiJobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAiJobNotFound(
            AiJobNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "AI job not found.",
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException exception,
            HttpServletRequest request
    ) {
        log.info("Authentication failed path={} reason={}", request.getRequestURI(), exception.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                exception.getMessage(),
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred.",
                request.getRequestURI(),
                extractClientRequestId(request),
                extractRequestId(request),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ApiFieldError toApiFieldError(FieldError fieldError) {
        return new ApiFieldError(
                fieldError.getField(),
                fieldError.getDefaultMessage()
        );
    }

    private String extractClientRequestId(HttpServletRequest request) {
        String clientRequestId = request.getHeader(RequestCorrelation.CLIENT_REQUEST_ID_HEADER);

        if (clientRequestId == null || clientRequestId.isBlank()) {
            return null;
        }

        return clientRequestId;
    }

    private String extractRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }

        return RequestCorrelation.currentRequestId();
    }
}
