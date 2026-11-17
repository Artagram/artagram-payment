package com.artagram.payment.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.artagram.payment.common.error.ApiException;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoProviderTest {

    private MPHttpClient original;
    private MPHttpClient http;
    private PagoProperties props;

    @BeforeEach
    void setUp() {
        original = MercadoPagoConfig.getHttpClient();
        http = mock(MPHttpClient.class);
        MercadoPagoConfig.setHttpClient(http);
        props = new PagoProperties();
        props.setMercadopagoAccessToken("TEST-token");
    }

    @AfterEach
    void restaurar() {
        MercadoPagoConfig.setHttpClient(original);
    }

    private static MPResponse respuesta(int status, String json) {
        return new MPResponse(status, Map.of(), json);
    }

    @Test
    void exigeElAccessToken() {
        PagoProperties sinToken = new PagoProperties();
        assertThatThrownBy(() -> new MercadoPagoProvider(sinToken)).isInstanceOf(IllegalStateException.class);
        sinToken.setMercadopagoAccessToken(" ");
        assertThatThrownBy(() -> new MercadoPagoProvider(sinToken)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void creaLaPreferenciaConElRetornoConfigurado() throws Exception {
        when(http.send(any())).thenReturn(respuesta(201, "{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.co/checkout?pref=1\"}"));
        props.setRetornoUrl("https://artagram.app/suscripcion/resultado");
        MercadoPagoProvider provider = new MercadoPagoProvider(props);

        var checkout = provider.crearPreferencia(UUID.randomUUID(), "PRO", "https://evil.example");

        assertThat(provider.getNombre()).isEqualTo("MERCADOPAGO");
        assertThat(checkout.idTransaccion()).isEqualTo("pref-1");
        assertThat(checkout.checkoutUrl()).startsWith("https://www.mercadopago.com.co/");
        assertThat(checkout.proveedor()).isEqualTo("MERCADOPAGO");
    }

    @Test
    void siMercadoPagoFallaAlCrearEs502() throws Exception {
        MercadoPagoProvider provider = new MercadoPagoProvider(props);
        when(http.send(any()))
                .thenThrow(new MPApiException("rechazo", respuesta(400, "{\"message\":\"bad\"}")))
                .thenThrow(new MPException("sin red"));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.crearPreferencia(UUID.randomUUID(), "PRO", null))
                    .isInstanceOf(ApiException.class)
                    .extracting("code").isEqualTo("PROVEEDOR_PAGO_NO_DISPONIBLE");
        }
    }

    @Test
    void consultaElPago() throws Exception {
        MercadoPagoProvider provider = new MercadoPagoProvider(props);
        when(http.send(any())).thenReturn(respuesta(200,
                "{\"id\":123,\"status\":\"approved\",\"external_reference\":\"ref\",\"transaction_amount\":20000,\"currency_id\":\"COP\"}"));

        var pago = provider.consultarPago("123").orElseThrow();

        assertThat(pago.id()).isEqualTo("123");
        assertThat(pago.estado()).isEqualTo("approved");
        assertThat(pago.referenciaExterna()).isEqualTo("ref");
        assertThat(pago.monto()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(pago.moneda()).isEqualTo("COP");
    }

    @Test
    void consultarPagoInexistenteOInvalidoDevuelveVacio() throws Exception {
        MercadoPagoProvider provider = new MercadoPagoProvider(props);
        when(http.send(any())).thenThrow(new MPApiException("no", respuesta(404, "{}")));

        assertThat(provider.consultarPago("no-numerico")).isEmpty();
        assertThat(provider.consultarPago("999")).isEmpty();
    }

    @Test
    void consultarPagoConErrorDelProveedorEs502() throws Exception {
        MercadoPagoProvider provider = new MercadoPagoProvider(props);
        when(http.send(any()))
                .thenThrow(new MPApiException("caido", respuesta(500, "{}")))
                .thenThrow(new MPException("sin red"));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.consultarPago("1")).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void elProveedorFakeSimulaElCheckout() {
        FakePagoProvider fake = new FakePagoProvider();
        UUID u = UUID.randomUUID();

        assertThat(fake.getNombre()).isEqualTo("FAKE");
        assertThat(fake.crearPreferencia(u, "PRO", "http://localhost:5173/r").checkoutUrl())
                .startsWith("http://localhost:5173/r?collection_status=approved&external_reference=sim_");
        assertThat(fake.crearPreferencia(u, "PRO", " ").checkoutUrl()).startsWith("/perfil/me?suscripcion=exitosa&tx=sim_");
        assertThat(fake.crearPreferencia(u, "PRO", null).proveedor()).isEqualTo("FAKE");
        assertThat(fake.consultarPago("1")).isEmpty();
    }

    @Test
    void propiedadesDePago() {
        PagoProperties p = new PagoProperties();
        assertThat(p.getProveedor()).isEqualTo("fake");
        assertThat(p.getProMoneda()).isEqualTo("COP");
        p.setProveedor("mercadopago");
        p.setProMoneda("USD");
        p.setProPrecio(BigDecimal.TEN);
        p.setMercadopagoWebhookSecret("s");
        assertThat(p.getProveedor()).isEqualTo("mercadopago");
        assertThat(p.getProMoneda()).isEqualTo("USD");
        assertThat(p.getProPrecio()).isEqualTo(BigDecimal.TEN);
        assertThat(p.getMercadopagoWebhookSecret()).isEqualTo("s");
    }
}
