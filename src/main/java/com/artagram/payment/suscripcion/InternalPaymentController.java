package com.artagram.payment.suscripcion;

import com.artagram.payment.suscripcion.dto.PlanDetalleDto;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Renombrado de /internal/users/{id}/plan — ahora vive en el servicio que de verdad es dueño del plan. */
@RestController
@RequestMapping("/internal/payment")
public class InternalPaymentController {

    private final SuscripcionService suscripcionService;

    public InternalPaymentController(SuscripcionService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @GetMapping("/{usuarioId}/plan")
    public ResponseEntity<PlanDetalleDto> obtenerPlan(@PathVariable UUID usuarioId) {
        return ResponseEntity.ok(suscripcionService.obtenerPlanDetalle(usuarioId));
    }
}
