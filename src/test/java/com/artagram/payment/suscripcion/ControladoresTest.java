package com.artagram.payment.suscripcion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artagram.payment.auth.JwtService;
import com.artagram.payment.auth.JwtUserResolver;
import com.artagram.payment.billing.MercadoPagoWebhookVerifier;
import com.artagram.payment.common.error.ApiException;
import com.artagram.payment.common.error.GlobalExceptionHandler;
import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import com.artagram.payment.suscripcion.dto.PlanDetalleDto;
import com.artagram.payment.suscripcion.dto.SuscripcionDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ControladoresTest {

    private final UUID yo = UUID.randomUUID();
    private SuscripcionService service;
    private MercadoPagoWebhookVerifier verifier;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SuscripcionService.class);
        verifier = mock(MercadoPagoWebhookVerifier.class);
        JwtUserResolver resolver = mock(JwtUserResolver.class);
        when(resolver.resolverRequerido(any())).thenReturn(new JwtService.TokenPrincipal(yo, "lucia", "USUARIO", false));
        mvc = MockMvcBuilders.standaloneSetup(
                        new SuscripcionController(service, resolver),
                        new PagoWebhookController(verifier, service),
                        new InternalPaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler(new SimpleMeterRegistry()))
                .build();
    }

    @Test
    void suscripcionActualCheckoutYConfirmacion() throws Exception {
        when(service.obtenerSuscripcion(yo)).thenReturn(new SuscripcionDto("GRATIS", "ACTIVA", "2026-01-01T00:00:00Z", null, false, 0, 512, 0, 50));
        when(service.crearCheckout(eq(yo), any())).thenReturn(new CheckoutResponse("https://mp/checkout", "tx", "FAKE"));
        when(service.obtenerPlanDetalle(yo)).thenReturn(new PlanDetalleDto(yo, "GRATIS", false, 512, 50));

        mvc.perform(get("/api/suscripcion/actual")).andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("GRATIS"));
        mvc.perform(post("/api/suscripcion/checkout").contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idTransaccion").value("tx"));
        mvc.perform(post("/api/suscripcion/confirmar")).andExpect(status().isOk());
        verify(service).confirmarSimulado(yo, "sim_manual");
        when(service.verificarPago(yo, "123")).thenReturn(true);
        mvc.perform(post("/api/suscripcion/verificar").param("pagoId", "123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.premium").value(true));
        mvc.perform(get("/internal/payment/" + yo + "/plan")).andExpect(status().isOk()).andExpect(jsonPath("$.maxObras").value(50));
    }

    @Test
    void webhookVerificaLaFirmaYSoloProcesaPagos() throws Exception {
        when(verifier.esValida("1", "req", "ts=1,v1=a")).thenReturn(true);

        mvc.perform(post("/api/pagos/webhook/mercadopago").param("data.id", "1").param("type", "payment")
                        .header("x-signature", "ts=1,v1=a").header("x-request-id", "req"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pagos/webhook/mercadopago").param("data.id", "1").param("type", "merchant_order")
                        .header("x-signature", "ts=1,v1=a").header("x-request-id", "req"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pagos/webhook/mercadopago").param("data.id", "2"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("FIRMA_INVALIDA"));

        verify(service).procesarNotificacionPago("1");
        verify(service, never()).procesarNotificacionPago("2");
    }

    @Test
    void erroresUsanElFormatoComun() throws Exception {
        when(service.obtenerSuscripcion(yo))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "PROVEEDOR", "x"))
                .thenThrow(new OptimisticLockingFailureException("carrera"))
                .thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/api/suscripcion/actual")).andExpect(status().isBadGateway());
        mvc.perform(get("/api/suscripcion/actual")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_CONCURRENCIA"));
        mvc.perform(get("/api/suscripcion/actual")).andExpect(status().isInternalServerError());
        mvc.perform(post("/api/suscripcion/checkout").contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        mvc.perform(post("/api/suscripcion/checkout").contentType(MediaType.APPLICATION_JSON).content("{x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));
        mvc.perform(put("/api/suscripcion/actual")).andExpect(status().isMethodNotAllowed());
    }
}
