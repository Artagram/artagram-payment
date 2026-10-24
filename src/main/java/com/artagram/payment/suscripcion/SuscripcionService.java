package com.artagram.payment.suscripcion;

import com.artagram.payment.billing.ProveedorPago;
import com.artagram.payment.events.PaymentEventPublisher;
import com.artagram.payment.suscripcion.dto.CheckoutRequest;
import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import com.artagram.payment.suscripcion.dto.PlanDetalleDto;
import com.artagram.payment.suscripcion.dto.SuscripcionDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A diferencia de la versión original en artagram-users, este servicio no
 * depende de un UsuarioRepository/Usuario — el estado "premium" vive
 * localmente (plan+estado de la Suscripcion), sembrado desde el evento
 * user.registered (ver PaymentUserEventListener) y actualizado aquí mismo
 * al aprobar un pago.
 */
@Service
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepo;
    private final UsoCuentaRepository usoRepo;
    private final ProveedorPago proveedorPago;
    private final PaymentEventPublisher eventPublisher;

    public SuscripcionService(SuscripcionRepository suscripcionRepo,
                               UsoCuentaRepository usoRepo,
                               ProveedorPago proveedorPago,
                               PaymentEventPublisher eventPublisher) {
        this.suscripcionRepo = suscripcionRepo;
        this.usoRepo = usoRepo;
        this.proveedorPago = proveedorPago;
        this.eventPublisher = eventPublisher;
    }

    public SuscripcionDto obtenerSuscripcion(UUID usuarioId) {
        Suscripcion sub = suscripcionActualONueva(usuarioId);
        boolean premium = esPremium(sub);
        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId)
                .orElseGet(() -> usoRepo.save(new UsoCuenta(usuarioId, 0, premium ? 5120 : 512, 0, premium ? 500 : 50)));

        return new SuscripcionDto(
                sub.getPlan(),
                sub.getEstado(),
                sub.getFechaInicio().toString(),
                sub.getFechaFin() != null ? sub.getFechaFin().toString() : null,
                premium,
                uso.getMegabytesUsados(),
                uso.getMaxMegabytes(),
                uso.getObrasSubidas(),
                uso.getMaxObras()
        );
    }

    public CheckoutResponse crearCheckout(UUID usuarioId, CheckoutRequest req) {
        return proveedorPago.crearPreferencia(usuarioId, req.plan(), req.retornoUrl());
    }

    @Transactional
    public void procesarPagoAprobado(UUID usuarioId, String idTransaccion) {
        Instant ahora = Instant.now();
        Instant fin = ahora.plus(30, ChronoUnit.DAYS);

        Suscripcion sub = new Suscripcion(usuarioId, "PRO", "ACTIVA", ahora, fin, proveedorPago.getNombre(), idTransaccion);
        suscripcionRepo.save(sub);

        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId).orElseGet(() -> new UsoCuenta(usuarioId, 0, 5120, 0, 500));
        uso.setMaxMegabytes(5120);
        uso.setMaxObras(500);
        usoRepo.save(uso);

        eventPublisher.publicarSuscripcionActualizada(usuarioId, true, "PRO");
    }

    public PlanDetalleDto obtenerPlanDetalle(UUID usuarioId) {
        Suscripcion sub = suscripcionActualONueva(usuarioId);
        boolean premium = esPremium(sub);
        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId)
                .orElseGet(() -> new UsoCuenta(usuarioId, 0, premium ? 5120 : 512, 0, premium ? 500 : 50));

        return new PlanDetalleDto(usuarioId, premium ? "PRO" : "GRATIS", premium, uso.getMaxMegabytes(), uso.getMaxObras());
    }

    private Suscripcion suscripcionActualONueva(UUID usuarioId) {
        return suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(usuarioId)
                .orElseGet(() -> new Suscripcion(usuarioId, "GRATIS", "ACTIVA", Instant.now(), null, "NINGUNO", null));
    }

    private static boolean esPremium(Suscripcion sub) {
        return "PRO".equals(sub.getPlan()) && "ACTIVA".equals(sub.getEstado());
    }
}
