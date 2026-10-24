package com.artagram.payment.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Genera (o reutiliza) un traceId por petición HTTP y lo deja en el MDC para que
 * todos los logs de esa interacción compartan un identificador.
 */
@Component
@Order(Integer.MIN_VALUE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TraceContext.TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
        MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
        MDC.put(TraceContext.OPERATION_MDC_KEY, request.getMethod() + " " + request.getRequestURI());
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
            MDC.remove(TraceContext.USER_ID_MDC_KEY);
            MDC.remove(TraceContext.OPERATION_MDC_KEY);
        }
    }
}
