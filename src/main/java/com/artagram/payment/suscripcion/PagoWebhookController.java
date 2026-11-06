package com.artagram.payment.suscripcion;

import com.artagram.payment.billing.MercadoPagoWebhookVerifier;
import com.artagram.payment.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receptor de webhooks de Mercado Pago. La notificación solo avisa que algo
 * cambió: el estado real del pago se consulta después con la API.
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
    public ResponseEntity<Void> mercadopago(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(required = false) String type,
            @RequestHeader(value = "x-signature", required = false) String firma,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {
        if (!verifier.esValida(dataId, requestId, firma)) {
            throw ApiException.unauthorized("FIRMA_INVALIDA", "La firma del webhook no es válida");
        }
        if ("payment".equals(type)) {
            suscripcionService.procesarNotificacionPago(dataId);
        }
        return ResponseEntity.ok().build();
    }
}
