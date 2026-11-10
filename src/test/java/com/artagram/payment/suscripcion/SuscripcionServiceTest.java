package com.artagram.payment.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.payment.billing.PagoConsultado;
import com.artagram.payment.billing.PagoProperties;
import com.artagram.payment.billing.ProveedorPago;
import com.artagram.payment.common.error.ApiException;
import com.artagram.payment.events.PaymentEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SuscripcionServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final BigDecimal PRECIO = new BigDecimal("20000");

    private SuscripcionRepository suscripcionRepo;
    private UsoCuentaRepository usoRepo;
    private PagoProcesadoRepository pagoProcesadoRepo;
    private ProveedorPago proveedor;
    private PaymentEventPublisher publisher;
    private SuscripcionService service;

    @BeforeEach
    void setUp() {
        suscripcionRepo = mock(SuscripcionRepository.class);
        usoRepo = mock(UsoCuentaRepository.class);
        pagoProcesadoRepo = mock(PagoProcesadoRepository.class);
        proveedor = mock(ProveedorPago.class);
        publisher = mock(PaymentEventPublisher.class);
        when(proveedor.getNombre()).thenReturn("MERCADOPAGO");
        when(pagoProcesadoRepo.registrar(anyString())).thenReturn(true);
        when(usoRepo.findByUsuarioId(any())).thenReturn(Optional.empty());
        when(suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(any())).thenReturn(Optional.empty());
        service = new SuscripcionService(suscripcionRepo, usoRepo, pagoProcesadoRepo, proveedor,
                new PagoProperties(), publisher);
    }

    private void pagoEs(String estado, String referencia, BigDecimal monto, String moneda) {
        when(proveedor.consultarPago("1")).thenReturn(
                Optional.of(new PagoConsultado("1", estado, referencia, monto, moneda)));
    }

    private void verificarQueNoActivo() {
        verify(suscripcionRepo, never()).save(any());
        verify(publisher, never()).publicarSuscripcionActualizada(any(), anyBoolean(), anyString());
    }

    @Test
    void pagoAprobadoActivaProPorTreintaDias() {
        pagoEs("approved", USUARIO.toString(), PRECIO, "COP");

        service.procesarNotificacionPago("1");

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepo).save(captor.capture());
        assertThat(captor.getValue().getPlan()).isEqualTo("PRO");
        assertThat(captor.getValue().getFechaFin())
                .isBetween(Instant.now().plus(29, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
        verify(publisher).publicarSuscripcionActualizada(USUARIO, true, "PRO");
    }

    @Test
    void pagoRepetidoNoSeAplicaDosVeces() {
        pagoEs("approved", USUARIO.toString(), PRECIO, "COP");
        when(pagoProcesadoRepo.registrar("1")).thenReturn(false);

        service.procesarNotificacionPago("1");

        verificarQueNoActivo();
    }

    @Test
    void pagoPendienteORechazadoNoActiva() {
        pagoEs("pending", USUARIO.toString(), PRECIO, "COP");
        service.procesarNotificacionPago("1");
        pagoEs("rejected", USUARIO.toString(), PRECIO, "COP");
        service.procesarNotificacionPago("1");

        verificarQueNoActivo();
        verify(pagoProcesadoRepo, never()).registrar(anyString());
    }

    @Test
    void pagoInexistenteSeIgnora() {
        when(proveedor.consultarPago("1")).thenReturn(Optional.empty());

        service.procesarNotificacionPago("1");

        verificarQueNoActivo();
    }

    @Test
    void referenciaInvalidaNoActiva() {
        pagoEs("approved", "no-es-un-uuid", PRECIO, "COP");
        service.procesarNotificacionPago("1");
        pagoEs("approved", null, PRECIO, "COP");
        service.procesarNotificacionPago("1");

        verificarQueNoActivo();
    }

    @Test
    void montoMenorOMonedaDistintaNoActiva() {
        pagoEs("approved", USUARIO.toString(), new BigDecimal("100"), "COP");
        service.procesarNotificacionPago("1");
        pagoEs("approved", USUARIO.toString(), PRECIO, "USD");
        service.procesarNotificacionPago("1");

        verificarQueNoActivo();
    }

    @Test
    void renovarExtiendeLaSuscripcionVigente() {
        Instant fin = Instant.now().plus(10, ChronoUnit.DAYS);
        Suscripcion vigente = new Suscripcion(USUARIO, "PRO", "ACTIVA", Instant.now(), fin, "MERCADOPAGO", "0");
        when(suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(USUARIO)).thenReturn(Optional.of(vigente));
        pagoEs("approved", USUARIO.toString(), PRECIO, "COP");

        service.procesarNotificacionPago("1");

        assertThat(vigente.getFechaFin()).isEqualTo(fin.plus(30, ChronoUnit.DAYS));
        assertThat(vigente.getIdTransaccion()).isEqualTo("1");
    }

    @Test
    void confirmarManualNoFuncionaConProveedorReal() {
        assertThatThrownBy(() -> service.confirmarSimulado(USUARIO, "tx"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONFIRMACION_NO_PERMITIDA");

        verificarQueNoActivo();
    }

    @Test
    void confirmarManualFuncionaConProveedorFake() {
        when(proveedor.getNombre()).thenReturn("FAKE");

        service.confirmarSimulado(USUARIO, "tx");

        verify(publisher).publicarSuscripcionActualizada(USUARIO, true, "PRO");
    }

    @Test
    void alVencerQuitaPremiumYVuelveALosLimitesGratis() {
        Suscripcion vencida = new Suscripcion(USUARIO, "PRO", "ACTIVA", Instant.now().minus(31, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS), "MERCADOPAGO", "1");
        UsoCuenta uso = new UsoCuenta(USUARIO, 0, 5120, 0, 500);
        when(suscripcionRepo.findByEstadoAndFechaFinBefore(anyString(), any())).thenReturn(List.of(vencida));
        when(suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(USUARIO)).thenReturn(Optional.of(vencida));
        when(usoRepo.findByUsuarioId(USUARIO)).thenReturn(Optional.of(uso));

        service.expirarVencidas();

        assertThat(vencida.getEstado()).isEqualTo("EXPIRADA");
        assertThat(uso.getMaxObras()).isEqualTo(50);
        verify(publisher).publicarSuscripcionActualizada(USUARIO, false, "GRATIS");
    }

    @Test
    void alVencerUnaFilaViejaNoQuitaPremiumSiHayOtraVigente() {
        Suscripcion vieja = new Suscripcion(USUARIO, "PRO", "ACTIVA", Instant.now().minus(40, ChronoUnit.DAYS),
                Instant.now().minus(10, ChronoUnit.DAYS), "MERCADOPAGO", "1");
        Suscripcion vigente = new Suscripcion(USUARIO, "PRO", "ACTIVA", Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().plus(25, ChronoUnit.DAYS), "MERCADOPAGO", "2");
        when(suscripcionRepo.findByEstadoAndFechaFinBefore(anyString(), any())).thenReturn(List.of(vieja));
        when(suscripcionRepo.findTopByUsuarioIdOrderByFechaInicioDesc(USUARIO)).thenReturn(Optional.of(vigente));

        service.expirarVencidas();

        assertThat(vieja.getEstado()).isEqualTo("EXPIRADA");
        verify(publisher, never()).publicarSuscripcionActualizada(any(), anyBoolean(), anyString());
    }
}
