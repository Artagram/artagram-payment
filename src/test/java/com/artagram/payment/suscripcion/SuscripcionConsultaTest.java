package com.artagram.payment.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.payment.billing.PagoProperties;
import com.artagram.payment.billing.ProveedorPago;
import com.artagram.payment.events.PaymentEventPublisher;
import com.artagram.payment.suscripcion.dto.CheckoutRequest;
import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SuscripcionConsultaTest {

    private final UUID u = UUID.randomUUID();
    private SuscripcionRepository subs;
    private UsoCuentaRepository usos;
    private ProveedorPago proveedor;
    private SuscripcionService service;

    @BeforeEach
    void setUp() {
        subs = mock(SuscripcionRepository.class);
        usos = mock(UsoCuentaRepository.class);
        proveedor = mock(ProveedorPago.class);
        when(subs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new SuscripcionService(subs, usos, mock(PagoProcesadoRepository.class), proveedor,
                new PagoProperties(), mock(PaymentEventPublisher.class));
    }

    @Test
    void suscripcionProVigenteConUsoExistente() {
        Instant fin = Instant.now().plus(10, ChronoUnit.DAYS);
        when(subs.findTopByUsuarioIdOrderByFechaInicioDesc(u))
                .thenReturn(Optional.of(new Suscripcion(u, "PRO", "ACTIVA", Instant.now(), fin, "FAKE", "tx")));
        when(usos.findByUsuarioId(u)).thenReturn(Optional.of(new UsoCuenta(u, 100, 5120, 3, 500)));

        var dto = service.obtenerSuscripcion(u);
        var plan = service.obtenerPlanDetalle(u);

        assertThat(dto.premium()).isTrue();
        assertThat(dto.fechaFin()).isEqualTo(fin.toString());
        assertThat(dto.megabytesUsados()).isEqualTo(100);
        assertThat(plan.plan()).isEqualTo("PRO");
        assertThat(plan.maxObras()).isEqualTo(500);
        verify(usos, never()).save(any());
    }

    @Test
    void usuarioSinDatosRecibeElPlanGratisPorDefecto() {
        when(subs.findTopByUsuarioIdOrderByFechaInicioDesc(u)).thenReturn(Optional.empty());
        when(usos.findByUsuarioId(u)).thenReturn(Optional.empty());

        var dto = service.obtenerSuscripcion(u);
        var plan = service.obtenerPlanDetalle(u);

        assertThat(dto.premium()).isFalse();
        assertThat(dto.fechaFin()).isNull();
        assertThat(dto.maxMegabytes()).isEqualTo(512);
        assertThat(plan.maxMegabytes()).isEqualTo(512);
        assertThat(plan.premium()).isFalse();
    }

    @Test
    void checkoutDelegaEnElProveedor() {
        CheckoutResponse esperado = new CheckoutResponse("url", "tx", "FAKE");
        when(proveedor.crearPreferencia(eq(u), eq("PRO"), anyString())).thenReturn(esperado);

        assertThat(service.crearCheckout(u, new CheckoutRequest("PRO", "http://r"))).isEqualTo(esperado);
    }

    @Test
    void pagoProcesadoSoloSeRegistraUnaVez() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("p1"))).thenReturn(1);
        when(jdbc.update(anyString(), eq("p2"))).thenReturn(0);
        PagoProcesadoRepository repo = new PagoProcesadoRepository(jdbc);

        assertThat(repo.registrar("p1")).isTrue();
        assertThat(repo.registrar("p2")).isFalse();
    }

    @Test
    void igualdadDeEntidadesPorId() throws Exception {
        Suscripcion a = new Suscripcion();
        Suscripcion b = new Suscripcion();
        var campo = com.artagram.payment.common.domain.BaseEntity.class.getDeclaredField("id");
        campo.setAccessible(true);
        UUID id = UUID.randomUUID();
        campo.set(a, id);
        campo.set(b, id);

        assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(new Suscripcion()).isNotEqualTo("otro");
        assertThat(new Suscripcion()).isNotEqualTo(new Suscripcion());
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
