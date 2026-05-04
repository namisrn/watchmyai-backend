package com.watchmyai.common.api;

import org.slf4j.MDC;

public final class RequestCorrelation {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CLIENT_REQUEST_ID_HEADER = "X-Client-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "watchmyai.requestId";
    public static final String MDC_REQUEST_ID = "requestId";

    private RequestCorrelation() {
    }

    public static String currentRequestId() {
        return MDC.get(MDC_REQUEST_ID);
    }
}
