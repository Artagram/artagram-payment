package com.artagram.payment.events;

import com.artagram.payment.suscripcion.Suscripcion;
import com.artagram.payment.suscripcion.SuscripcionRepository;
import com.artagram.payment.suscripcion.UsoCuenta;
import com.artagram.payment.suscripcion.UsoCuentaRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirror de UsersEventListener en artagram-users, pero sembrando Suscripcion
 * y UsoCuenta en vez de Usuario/Perfil. A diferencia de artagram-canvas (que
 * conserva la proyección al borrar un usuario), aquí se eliminan las filas:
 * no tienen valor de auditoría una vez que la cuenta desaparece, y
 * conservarlas sería retener datos de facturación sin motivo.
 */
@Component
public class PaymentUserEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentUserEventListener.class);

    private final SuscripcionRepository suscripcionRepo;
    private final UsoCuentaRepository usoRepo;

    public PaymentUserEventListener(SuscripcionRepository suscripcionRepo, UsoCuentaRepository usoRepo) {
        this.suscripcionRepo = suscripcionRepo;
        this.usoRepo = usoRepo;
    }

    @RabbitListener(queues = PaymentEventsRabbitMqConfig.QUEUE_USER_REGISTERED)
    @Transactional
    public void onUserRegistered(Map<String, Object> payload) {
        try {
            UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId") != null ? payload.get("usuarioId") : payload.get("id")));
            boolean premium = Boolean.TRUE.equals(payload.get("premium"));

            if (suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(usuarioId).isEmpty()) {
                suscripcionRepo.save(new Suscripcion(
                        usuarioId, premium ? "PRO" : "GRATIS", "ACTIVA", Instant.now(), null, "NINGUNO", null));
            }
            if (usoRepo.findByUsuarioId(usuarioId).isEmpty()) {
                usoRepo.save(new UsoCuenta(usuarioId, 0, premium ? 5120 : 512, 0, premium ? 500 : 50));
            }
            log.info("Suscripción/uso de cuenta sincronizados en artagram-payment para usuarioId={}", usuarioId);
        } catch (Exception ex) {
            log.warn("Error al procesar user.registered en artagram-payment: {}", ex.getMessage());
        }
    }

    @RabbitListener(queues = PaymentEventsRabbitMqConfig.QUEUE_USER_DELETED)
    @Transactional
    public void onUserDeleted(Map<String, Object> payload) {
        try {
            UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId") != null ? payload.get("usuarioId") : payload.get("id")));
            suscripcionRepo.deleteAllByUsuarioId(usuarioId);
            usoRepo.deleteByUsuarioId(usuarioId);
            log.info("Suscripción/uso de cuenta eliminados en artagram-payment para usuarioId={}", usuarioId);
        } catch (Exception ex) {
            log.warn("Error al procesar user.deleted en artagram-payment: {}", ex.getMessage());
        }
    }
}
