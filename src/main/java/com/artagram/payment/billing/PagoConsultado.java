package com.artagram.payment.billing;

import java.math.BigDecimal;

/** Estado real de un pago, consultado al proveedor (nunca tomado del webhook). */
public record PagoConsultado(String id, String estado, String referenciaExterna, BigDecimal monto, String moneda) {

    public boolean aprobado() {
        return "approved".equals(estado);
    }
}
