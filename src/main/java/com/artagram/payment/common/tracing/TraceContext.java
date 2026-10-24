package com.artagram.payment.common.tracing;

/**
 * Claves de MDC compartidas para que un mismo request deje logs correlacionados.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String USER_ID_MDC_KEY = "usuarioId";
    public static final String OPERATION_MDC_KEY = "operacion";

    private TraceContext() {
    }
}
