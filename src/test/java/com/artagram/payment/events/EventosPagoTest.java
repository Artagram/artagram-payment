package com.artagram.payment.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.payment.suscripcion.Suscripcion;
import com.artagram.payment.suscripcion.SuscripcionRepository;
import com.artagram.payment.suscripcion.SuscripcionService;
import com.artagram.payment.suscripcion.SuscripcionVencimientoJob;
import com.artagram.payment.suscripcion.UsoCuenta;
import com.artagram.payment.suscripcion.UsoCuentaRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class EventosPagoTest {

    private final SuscripcionRepository subs = mock(SuscripcionRepository.class);
    private final UsoCuentaRepository usos = mock(UsoCuentaRepository.class);
    private final PaymentUserEventListener listener = new PaymentUserEventListener(subs, usos);
    private final UUID u = UUID.randomUUID();

    @Test
    void usuarioRegistradoPremiumSiembraPlanProYCuotas() {
        when(subs.findTopByUsuarioIdOrderByFechaInicioDesc(u)).thenReturn(Optional.empty());
        when(usos.findByUsuarioId(u)).thenReturn(Optional.empty());

        listener.onUserRegistered(Map.of("usuarioId", u.toString(), "premium", true));

        ArgumentCaptor<Suscripcion> s = ArgumentCaptor.forClass(Suscripcion.class);
        ArgumentCaptor<UsoCuenta> uso = ArgumentCaptor.forClass(UsoCuenta.class);
        verify(subs).save(s.capture());
        verify(usos).save(uso.capture());
        assertThat(s.getValue().getPlan()).isEqualTo("PRO");
        assertThat(uso.getValue().getMaxMegabytes()).isEqualTo(5120);
        assertThat(uso.getValue().getMaxObras()).isEqualTo(500);
    }

    @Test
    void usuarioRegistradoYaExistenteNoDuplicaYAceptaElCampoId() {
        when(subs.findTopByUsuarioIdOrderByFechaInicioDesc(u)).thenReturn(Optional.of(new Suscripcion()));
        when(usos.findByUsuarioId(u)).thenReturn(Optional.of(new UsoCuenta()));

        listener.onUserRegistered(Map.of("id", u.toString()));
        listener.onUserRegistered(Map.of("id", "no-uuid"));

        verify(subs, never()).save(any());
        verify(usos, never()).save(any());
    }

    @Test
    void usuarioEliminadoBorraSusDatos() {
        listener.onUserDeleted(Map.of("usuarioId", u.toString()));
        listener.onUserDeleted(Map.of("id", "no-uuid"));

        verify(subs).deleteAllByUsuarioId(u);
        verify(usos).deleteByUsuarioId(u);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicaSuscripcionActualizada() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new PaymentEventPublisher(rabbit).publicarSuscripcionActualizada(u, true, "PRO");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq("artagram.events"), eq("subscription.updated"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue()).containsEntry("premium", true).containsEntry("plan", "PRO");
    }

    @Test
    void configuracionRabbitYJobDeVencimiento() {
        PaymentEventsRabbitMqConfig c = new PaymentEventsRabbitMqConfig();
        var ex = c.eventsExchange();
        assertThat(c.jsonMessageConverter()).isNotNull();
        assertThat(c.paymentUserRegisteredBinding(c.paymentUserRegisteredQueue(), ex).getRoutingKey()).isEqualTo("user.registered");
        assertThat(c.paymentUserDeletedBinding(c.paymentUserDeletedQueue(), ex).getRoutingKey()).isEqualTo("user.deleted");
        assertThat(c.paymentUserDeletedQueue().getArguments()).containsEntry("x-dead-letter-exchange", "artagram.dlx");

        SuscripcionService service = mock(SuscripcionService.class);
        new SuscripcionVencimientoJob(service).expirarVencidas();
        verify(service).expirarVencidas();
    }

    @Test
    void entidadesExponenSusCampos() {
        UsoCuenta uso = new UsoCuenta(u, 1, 2, 3, 4);
        uso.setMegabytesUsados(10);
        uso.setMaxMegabytes(20);
        uso.setObrasSubidas(5);
        uso.setMaxObras(6);
        assertThat(uso.getUsuarioId()).isEqualTo(u);
        assertThat(uso.getMegabytesUsados()).isEqualTo(10);
        assertThat(uso.getMaxMegabytes()).isEqualTo(20);
        assertThat(uso.getObrasSubidas()).isEqualTo(5);
        assertThat(uso.getMaxObras()).isEqualTo(6);

        Suscripcion s = new Suscripcion(u, "GRATIS", "ACTIVA", null, null, "NINGUNO", null);
        s.setPlan("PRO");
        s.setProveedorPago("FAKE");
        s.setIdTransaccion("tx");
        assertThat(s.getFechaInicio()).isNotNull();
        assertThat(s.getProveedorPago()).isEqualTo("FAKE");
        assertThat(s.getIdTransaccion()).isEqualTo("tx");
        assertThat(s.getId()).isNull();
        assertThat(s.getCreadoEn()).isNull();
        assertThat(s.getActualizadoEn()).isNull();
    }
}
