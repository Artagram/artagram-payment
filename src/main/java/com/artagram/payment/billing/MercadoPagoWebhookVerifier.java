package com.artagram.payment.billing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifica la firma de un webhook de Mercado Pago. El header
 * {@code x-signature} tiene la forma {@code ts=...,v1=...}, donde v1 es el
 * HMAC-SHA256 en hexadecimal del manifiesto
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}.
 * <p>
 * Sin secreto configurado se rechaza todo: un webhook sin verificar podría
 * activar suscripciones.
 */
@Component
public class MercadoPagoWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookVerifier.class);
    private static final String ALGORITMO = "HmacSHA256";

    private final PagoProperties pagoProperties;

    public MercadoPagoWebhookVerifier(PagoProperties pagoProperties) {
        this.pagoProperties = pagoProperties;
    }

    public boolean esValida(String dataId, String requestId, String firma) {
        String secreto = pagoProperties.getMercadopagoWebhookSecret();
        if (secreto == null || secreto.isBlank()) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET no configurado: se rechaza el webhook");
            return false;
        }
        if (esVacio(dataId) || esVacio(requestId) || esVacio(firma)) {
            return false;
        }
        String ts = parte(firma, "ts");
        String v1 = parte(firma, "v1");
        if (ts == null || v1 == null) {
            return false;
        }
        String manifiesto = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            String calculada = HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    calculada.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            log.warn("No se pudo verificar la firma del webhook de Mercado Pago: {}", e.getMessage());
            return false;
        }
    }

    private static String parte(String firma, String clave) {
        for (String par : firma.split(",")) {
            String[] kv = par.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(clave)) {
                return kv[1].trim();
            }
        }
        return null;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
