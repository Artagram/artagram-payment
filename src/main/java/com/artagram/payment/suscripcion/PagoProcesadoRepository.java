package com.artagram.payment.suscripcion;

import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El INSERT ... ON CONFLICT hace la comprobación y el registro en un solo paso
 * atómico: dos notificaciones simultáneas del mismo pago no pueden pasar ambas.
 */
@Repository
public class PagoProcesadoRepository {

    private final JdbcTemplate jdbcTemplate;

    public PagoProcesadoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return true si es la primera vez que se registra este pago. */
    public boolean registrar(String paymentId) {
        return jdbcTemplate.update(
                "INSERT INTO pagos_procesados (payment_id) VALUES (?) ON CONFLICT (payment_id) DO NOTHING",
                paymentId) == 1;
    }
}
