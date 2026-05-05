package com.watchmyai.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        request.setAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        MDC.put(RequestCorrelation.MDC_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestCorrelation.MDC_REQUEST_ID);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        if (headerValue != null && isReasonableHeaderValue(headerValue)) {
            return headerValue.trim();
        }

        return UUID.randomUUID().toString();
    }

    private boolean isReasonableHeaderValue(String value) {
        String trimmed = value.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= 100
                && trimmed.matches("[A-Za-z0-9._:-]+");
    }
}
