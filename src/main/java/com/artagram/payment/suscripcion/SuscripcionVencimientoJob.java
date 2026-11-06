package com.artagram.payment.suscripcion;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SuscripcionVencimientoJob {

    private final SuscripcionService suscripcionService;

    public SuscripcionVencimientoJob(SuscripcionService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @Scheduled(cron = "${app.pago.vencimiento-cron:0 0 * * * *}")
    public void expirarVencidas() {
        suscripcionService.expirarVencidas();
    }
}
