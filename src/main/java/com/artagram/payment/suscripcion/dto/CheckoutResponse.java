package com.artagram.payment.suscripcion.dto;

public record CheckoutResponse(
        String checkoutUrl,
        String idTransaccion,
        String proveedor
) {
}
