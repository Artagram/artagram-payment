package com.artagram.payment.suscripcion;

import com.artagram.payment.billing.MercadoPagoWebhookVerifier;
import com.artagram.payment.common.error.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receptor de webhooks de proveedores de pago. No existía nada equivalente
 * en artagram-users: el único flujo ahí era el "confirmar" manual/JWT que
 * se mantiene en SuscripcionController. Este es el único endpoint
 * genuinamente nuevo de esta migración (no una migración de código).
 */
@RestController
@RequestMapping("/api/pagos/webhook")
public class PagoWebhookController {

    private final MercadoPagoWebhookVerifier verifier;
    private final SuscripcionService suscripcionService;

    public PagoWebhookController(MercadoPagoWebhookVerifier verifier, SuscripcionService suscripcionService) {
        this.verifier = verifier;
        this.suscripcionService = suscripcionService;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<Map<String, String>> mercadopago(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Signature", required = false) String firma) {
        String rawBody = payload.toString();
        if (!verifier.esValida(rawBody, firma)) {
            throw ApiException.unauthorized("FIRMA_INVALIDA", "La firma del webhook no es válida");
        }
        UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId")));
        String idTransaccion = String.valueOf(payload.get("idTransaccion"));
        suscripcionService.procesarPagoAprobado(usuarioId, idTransaccion);
        return ResponseEntity.ok(Map.of("mensaje", "Pago procesado"));
    }
}
