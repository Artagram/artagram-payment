package com.artagram.payment.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookVerifierTest {

    private static final String SECRETO = "secreto-de-prueba";

    private PagoProperties properties;
    private MercadoPagoWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new PagoProperties();
        properties.setMercadopagoWebhookSecret(SECRETO);
        verifier = new MercadoPagoWebhookVerifier(properties);
    }

    private static String firmar(String manifiesto, String secreto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void firmaDelManifiestoEsValida() throws Exception {
        String v1 = firmar("id:123;request-id:req-1;ts:1704908010;", SECRETO);

        assertThat(verifier.esValida("123", "req-1", "ts=1704908010,v1=" + v1)).isTrue();
    }

    @Test
    void aceptaEspaciosYOrdenDistintoEnElHeader() throws Exception {
        String v1 = firmar("id:123;request-id:req-1;ts:1704908010;", SECRETO);

        assertThat(verifier.esValida("123", "req-1", "v1=" + v1 + ", ts=1704908010")).isTrue();
    }

    @Test
    void elIdSeFirmaEnMinusculas() throws Exception {
        String v1 = firmar("id:abc;request-id:req-1;ts:1;", SECRETO);

        assertThat(verifier.esValida("ABC", "req-1", "ts=1,v1=" + v1)).isTrue();
    }

    @Test
    void firmaConOtroSecretoSeRechaza() throws Exception {
        String v1 = firmar("id:123;request-id:req-1;ts:1;", "otro");

        assertThat(verifier.esValida("123", "req-1", "ts=1,v1=" + v1)).isFalse();
    }

    @Test
    void firmaDeOtroPagoSeRechaza() throws Exception {
        String v1 = firmar("id:123;request-id:req-1;ts:1;", SECRETO);

        assertThat(verifier.esValida("999", "req-1", "ts=1,v1=" + v1)).isFalse();
    }

    @Test
    void sinFirmaOConFormatoInvalidoSeRechaza() {
        assertThat(verifier.esValida("123", "req-1", null)).isFalse();
        assertThat(verifier.esValida("123", "req-1", "no-es-una-firma")).isFalse();
        assertThat(verifier.esValida("123", null, "ts=1,v1=abc")).isFalse();
        assertThat(verifier.esValida(null, "req-1", "ts=1,v1=abc")).isFalse();
    }

    @Test
    void sinSecretoConfiguradoSeRechazaTodo() throws Exception {
        String v1 = firmar("id:123;request-id:req-1;ts:1;", SECRETO);
        properties.setMercadopagoWebhookSecret("");

        assertThat(verifier.esValida("123", "req-1", "ts=1,v1=" + v1)).isFalse();
    }
}
