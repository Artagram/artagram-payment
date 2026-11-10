package com.artagram.payment.billing;

import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import java.util.Optional;
import java.util.UUID;

public interface ProveedorPago {
    String getNombre();
    CheckoutResponse crearPreferencia(UUID usuarioId, String plan, String retornoUrl);

    /** Consulta un pago al proveedor; vacío si no existe. */
    Optional<PagoConsultado> consultarPago(String idPago);
}
