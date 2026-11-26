package com.medexa.common.web;

import org.slf4j.MDC;

public final class CorrelationIdContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
