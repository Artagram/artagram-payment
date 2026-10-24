package com.artagram.payment.suscripcion.dto;

import java.util.UUID;

public record PlanDetalleDto(
        UUID usuarioId,
        String plan,
        boolean premium,
        long maxMegabytes,
        int maxObras
) {
}
