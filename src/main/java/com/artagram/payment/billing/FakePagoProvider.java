package com.artagram.payment.billing;

import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Proveedor de pago simulado (sin integración real) — proveedor por defecto
 * en dev/test y fallback para cuando no hay token de MercadoPago configurado.
 */
@Component
@ConditionalOnProperty(prefix = "app.pago", name = "proveedor", havingValue = "fake", matchIfMissing = true)
public class FakePagoProvider implements ProveedorPago {

    @Override
    public String getNombre() {
        return "FAKE";
    }

    @Override
    public CheckoutResponse crearPreferencia(UUID usuarioId, String plan, String retornoUrl) {
        String txId = "sim_" + UUID.randomUUID().toString().substring(0, 12);
        String url = (retornoUrl != null && !retornoUrl.isBlank())
                ? retornoUrl + "?collection_status=approved&external_reference=" + txId
                : "/perfil/me?suscripcion=exitosa&tx=" + txId;
        return new CheckoutResponse(url, txId, "FAKE");
    }
}
