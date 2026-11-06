package com.artagram.payment.suscripcion;

import com.artagram.payment.billing.PagoConsultado;
import com.artagram.payment.billing.PagoProperties;
import com.artagram.payment.billing.ProveedorPago;
import com.artagram.payment.common.error.ApiException;
import com.artagram.payment.events.PaymentEventPublisher;
import com.artagram.payment.suscripcion.dto.CheckoutRequest;
import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import com.artagram.payment.suscripcion.dto.PlanDetalleDto;
import com.artagram.payment.suscripcion.dto.SuscripcionDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);
    private static final String ESTADO_ACTIVA = "ACTIVA";
    private static final int DIAS_PRO = 30;
    private static final int MB_GRATIS = 512;
    private static final int MB_PRO = 5120;
    private static final int OBRAS_GRATIS = 50;
    private static final int OBRAS_PRO = 500;

    private final SuscripcionRepository suscripcionRepo;
    private final UsoCuentaRepository usoRepo;
    private final PagoProcesadoRepository pagoProcesadoRepo;
    private final ProveedorPago proveedorPago;
    private final PagoProperties pagoProperties;
    private final PaymentEventPublisher eventPublisher;

    public SuscripcionService(SuscripcionRepository suscripcionRepo,
                               UsoCuentaRepository usoRepo,
                               PagoProcesadoRepository pagoProcesadoRepo,
                               ProveedorPago proveedorPago,
                               PagoProperties pagoProperties,
                               PaymentEventPublisher eventPublisher) {
        this.suscripcionRepo = suscripcionRepo;
        this.usoRepo = usoRepo;
        this.pagoProcesadoRepo = pagoProcesadoRepo;
        this.proveedorPago = proveedorPago;
        this.pagoProperties = pagoProperties;
        this.eventPublisher = eventPublisher;
    }

    public SuscripcionDto obtenerSuscripcion(UUID usuarioId) {
        Suscripcion sub = suscripcionActualONueva(usuarioId);
        boolean premium = esPremium(sub);
        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId)
                .orElseGet(() -> usoRepo.save(new UsoCuenta(usuarioId, 0, premium ? MB_PRO : MB_GRATIS, 0, premium ? OBRAS_PRO : OBRAS_GRATIS)));

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

    /**
     * Procesa la notificación de un pago. Nada se toma del webhook: el estado,
     * el usuario y el monto vienen de la consulta al proveedor. Si algo falla,
     * la transacción se revierte (incluido el registro de idempotencia) y el
     * proveedor reintenta.
     */
    @Transactional
    public void procesarNotificacionPago(String idPago) {
        PagoConsultado pago = proveedorPago.consultarPago(idPago).orElse(null);
        if (pago == null) {
            log.warn("Notificación de un pago inexistente: {}", idPago);
            return;
        }
        if (!pago.aprobado()) {
            log.info("Pago {} en estado {}: no activa la suscripción", idPago, pago.estado());
            return;
        }
        Optional<UUID> usuarioId = parsearUsuario(pago.referenciaExterna());
        if (usuarioId.isEmpty()) {
            log.warn("Pago {} con referencia externa inválida: {}", idPago, pago.referenciaExterna());
            return;
        }
        if (!montoCorrecto(pago)) {
            log.warn("Pago {} con monto inesperado: {} {}", idPago, pago.monto(), pago.moneda());
            return;
        }
        if (!pagoProcesadoRepo.registrar(pago.id())) {
            log.info("Pago {} ya procesado: se ignora la notificación repetida", idPago);
            return;
        }
        activarPro(usuarioId.get(), pago.id());
    }

    /** Solo existe para desarrollo: con un proveedor real activaría PRO sin pagar. */
    @Transactional
    public void confirmarSimulado(UUID usuarioId, String idTransaccion) {
        if (!"FAKE".equals(proveedorPago.getNombre())) {
            throw ApiException.forbidden("CONFIRMACION_NO_PERMITIDA", "La suscripción se activa al aprobarse el pago");
        }
        activarPro(usuarioId, idTransaccion);
    }

    /**
     * Pasa a EXPIRADA lo que venció. Solo avisa la pérdida de premium si el
     * usuario no tiene otra suscripción vigente.
     */
    @Transactional
    public void expirarVencidas() {
        Instant ahora = Instant.now();
        for (Suscripcion sub : suscripcionRepo.findByEstadoAndFechaFinBefore(ESTADO_ACTIVA, ahora)) {
            sub.setEstado("EXPIRADA");
            suscripcionRepo.saveAndFlush(sub);
            UUID usuarioId = sub.getUsuarioId();
            if (!esPremium(suscripcionActualONueva(usuarioId))) {
                usoRepo.findByUsuarioId(usuarioId).ifPresent(uso -> {
                    uso.setMaxMegabytes(MB_GRATIS);
                    uso.setMaxObras(OBRAS_GRATIS);
                    usoRepo.save(uso);
                });
                eventPublisher.publicarSuscripcionActualizada(usuarioId, false, "GRATIS");
            }
        }
    }

    /** Una renovación extiende la suscripción vigente: no se pierden los días que quedaban. */
    private void activarPro(UUID usuarioId, String idTransaccion) {
        Instant ahora = Instant.now();
        Suscripcion actual = suscripcionActualONueva(usuarioId);
        if (esPremium(actual) && actual.getFechaFin() != null) {
            actual.setFechaFin(actual.getFechaFin().plus(DIAS_PRO, ChronoUnit.DAYS));
            actual.setIdTransaccion(idTransaccion);
            suscripcionRepo.save(actual);
        } else {
            suscripcionRepo.save(new Suscripcion(usuarioId, "PRO", ESTADO_ACTIVA, ahora,
                    ahora.plus(DIAS_PRO, ChronoUnit.DAYS), proveedorPago.getNombre(), idTransaccion));
        }

        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId)
                .orElseGet(() -> new UsoCuenta(usuarioId, 0, MB_PRO, 0, OBRAS_PRO));
        uso.setMaxMegabytes(MB_PRO);
        uso.setMaxObras(OBRAS_PRO);
        usoRepo.save(uso);

        eventPublisher.publicarSuscripcionActualizada(usuarioId, true, "PRO");
    }

    private boolean montoCorrecto(PagoConsultado pago) {
        return pago.monto() != null
                && pago.monto().compareTo(pagoProperties.getProPrecio()) >= 0
                && pagoProperties.getProMoneda().equals(pago.moneda());
    }

    private static Optional<UUID> parsearUsuario(String referencia) {
        try {
            return Optional.of(UUID.fromString(referencia));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public PlanDetalleDto obtenerPlanDetalle(UUID usuarioId) {
        Suscripcion sub = suscripcionActualONueva(usuarioId);
        boolean premium = esPremium(sub);
        UsoCuenta uso = usoRepo.findByUsuarioId(usuarioId)
                .orElseGet(() -> new UsoCuenta(usuarioId, 0, premium ? MB_PRO : MB_GRATIS, 0, premium ? OBRAS_PRO : OBRAS_GRATIS));

        return new PlanDetalleDto(usuarioId, premium ? "PRO" : "GRATIS", premium, uso.getMaxMegabytes(), uso.getMaxObras());
    }

    private Suscripcion suscripcionActualONueva(UUID usuarioId) {
        return suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(usuarioId)
                .orElseGet(() -> new Suscripcion(usuarioId, "GRATIS", "ACTIVA", Instant.now(), null, "NINGUNO", null));
    }

    private static boolean esPremium(Suscripcion sub) {
        return "PRO".equals(sub.getPlan()) && ESTADO_ACTIVA.equals(sub.getEstado())
                && (sub.getFechaFin() == null || sub.getFechaFin().isAfter(Instant.now()));
    }
}
