package com.artagram.payment.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Payload idéntico, campo por campo, al que publicaba
     * UsersEventPublisher.publicarSuscripcionActualizada en artagram-users,
     * para que UsuarioProyeccionEventListener (artagram-canvas) y cualquier
     * otro consumidor existente sigan funcionando sin cambios.
     */
    public void publicarSuscripcionActualizada(UUID usuarioId, boolean premium, String plan) {
        Map<String, Object> evento = Map.of(
                "tipo", "SUSCRIPCION_ACTUALIZADA",
                "usuarioId", usuarioId.toString(),
                "premium", premium,
                "plan", plan,
                "timestamp", Instant.now().toString()
        );
        log.info("Publicando subscription.updated para usuarioId={}, plan={}", usuarioId, plan);
        rabbitTemplate.convertAndSend(PaymentEventsRabbitMqConfig.EXCHANGE, "subscription.updated", evento);
    }
}
