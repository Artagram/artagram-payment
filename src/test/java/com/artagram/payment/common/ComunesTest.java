package com.artagram.payment.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.artagram.payment.common.config.CorsProperties;
import com.artagram.payment.common.config.WebConfig;
import com.artagram.payment.common.error.ApiException;
import com.artagram.payment.common.error.GlobalExceptionHandler;
import com.artagram.payment.common.tracing.TraceContext;
import com.artagram.payment.common.tracing.TraceIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ComunesTest {

    @Test
    void traceIdFilter() throws Exception {
        TraceIdFilter filtro = new TraceIdFilter();
        for (String recibido : new String[]{"trace-1", null, " "}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/suscripcion/actual");
            if (recibido != null) req.addHeader(TraceContext.TRACE_ID_HEADER, recibido);
            MockHttpServletResponse res = new MockHttpServletResponse();
            AtomicReference<String> visto = new AtomicReference<>();
            filtro.doFilter(req, res, (a, b) -> visto.set(MDC.get(TraceContext.TRACE_ID_MDC_KEY)));
            assertThat(res.getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo(visto.get());
            assertThat(MDC.get(TraceContext.TRACE_ID_MDC_KEY)).isNull();
        }
    }

    @Test
    void manejadorDeErrores() throws Exception {
        GlobalExceptionHandler h = new GlobalExceptionHandler(new SimpleMeterRegistry());

        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.rejectValue(null, "x");
        br.addError(new org.springframework.validation.FieldError("obj", "campo", "malo"));
        MethodParameter param = new MethodParameter(ComunesTest.class.getDeclaredMethod("manejadorDeErrores"), -1);
        var validacion = h.handleValidation(new MethodArgumentNotValidException(param, br));
        assertThat(validacion.getBody().code()).isEqualTo("VALIDACION");
        assertThat(validacion.getBody().violations()).extracting("field").containsExactly("campo");

        assertThat(h.handleFaltaParametro(new MissingServletRequestParameterException("p", "String")).getBody().code())
                .isEqualTo("PARAMETRO_FALTANTE");
        assertThat(h.handleRecursoNoEncontrado(mock(NoResourceFoundException.class)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(h.handleCuerpoInvalido(new HttpMessageNotReadableException("x", mock(HttpInputMessage.class))).getBody().code())
                .isEqualTo("CUERPO_INVALIDO");

        assertThat(ApiException.badRequest("A", "a").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiException.conflict("B", "b").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ApiException.notFound("C", "c").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiException.forbidden("D", "d").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void corsUsaLosOrigenesConfigurados() {
        CorsProperties props = new CorsProperties();
        assertThat(props.getAllowedOrigins()).containsExactly("http://localhost:5173");
        props.setAllowedOrigins(List.of("https://artagram.app"));
        CorsRegistry registry = new CorsRegistry();
        new WebConfig(props).addCorsMappings(registry);
        java.util.Map<String, org.springframework.web.cors.CorsConfiguration> mapeos =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(registry, "getCorsConfigurations");
        org.springframework.web.cors.CorsConfiguration config = mapeos.get("/**");
        assertThat(config.getAllowedOriginPatterns()).containsExactly("https://artagram.app");
        assertThat(config.getAllowedMethods()).contains("GET", "PATCH", "DELETE");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
