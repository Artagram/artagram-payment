package com.artagram.payment.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifica la firma de un webhook de MercadoPago contra
 * {@code app.pago.mercadopago-webhook-secret} (HMAC-SHA256 sobre el cuerpo
 * crudo de la petición). Es una verificación simplificada — el formato real
 * de firma de MercadoPago usa un manifiesto distinto (ts+id+request-id) —
 * consistente con que el resto de este servicio sigue siendo un stub, no
 * una integración real con el SDK de MercadoPago.
 * <p>
 * Si el secreto está en blanco (modo dev/Fake), se loguea un warning y NO
 * se verifica — misma postura "warn y continuar" que JwtService cuando no
 * encuentra la llave pública.
 */
@Component
public class MercadoPagoWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookVerifier.class);

    private final PagoProperties pagoProperties;

    public MercadoPagoWebhookVerifier(PagoProperties pagoProperties) {
        this.pagoProperties = pagoProperties;
    }

    public boolean esValida(String rawBody, String firmaRecibida) {
        String secreto = pagoProperties.getMercadopagoWebhookSecret();
        if (secreto == null || secreto.isBlank()) {
            log.warn("MERCADOPAGO_WEBHOOK_SECRET no configurado: se omite la verificación de firma (modo dev)");
            return true;
        }
        if (firmaRecibida == null || firmaRecibida.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculada = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String calculadaHex = bytesToHex(calculada);
            return MessageDigest.isEqual(
                    calculadaHex.getBytes(StandardCharsets.UTF_8),
                    firmaRecibida.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("No se pudo verificar la firma del webhook de MercadoPago: {}", e.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
