package com.artagram.payment.billing;

import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Proveedor de pago MercadoPago. A diferencia de la versión original en
 * artagram-users, la selección Fake-vs-MercadoPago NO vive dentro de este
 * bean (no hay fallback interno por token en blanco ni @Primary) — la
 * selecciona Spring vía {@code app.pago.proveedor} (env {@code PAGO_PROVEEDOR}),
 * el mismo patrón @ConditionalOnProperty que usan StorageProvider/EmailService
 * en el resto del workspace. La existencia del bean ES la selección.
 * <p>
 * Sigue siendo un stub (no llama al SDK/API real de MercadoPago) — eso no
 * cambia respecto al código original, solo se corrige cómo se elige el bean.
 */
@Component
@ConditionalOnProperty(prefix = "app.pago", name = "proveedor", havingValue = "mercadopago")
public class MercadoPagoProvider implements ProveedorPago {

    @Override
    public String getNombre() {
        return "MERCADOPAGO";
    }

    @Override
    public CheckoutResponse crearPreferencia(UUID usuarioId, String plan, String retornoUrl) {
        String txId = "mp_" + UUID.randomUUID().toString().substring(0, 12);
        String url = (retornoUrl != null && !retornoUrl.isBlank())
                ? retornoUrl + "?collection_status=approved&external_reference=" + txId
                : "/perfil/me?suscripcion=exitosa&tx=" + txId;
        return new CheckoutResponse(url, txId, "MERCADOPAGO");
    }
}
