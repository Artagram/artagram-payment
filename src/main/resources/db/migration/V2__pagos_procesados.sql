-- V2__pagos_procesados.sql
-- Idempotencia de pagos: Mercado Pago reintenta y repite notificaciones, y un
-- mismo pago no debe sumar dos veces el periodo PRO. Solo guarda el id del
-- pago: no hay datos del usuario que retener cuando borra su cuenta.

CREATE TABLE IF NOT EXISTS pagos_procesados (
    payment_id   VARCHAR(100) PRIMARY KEY,
    procesado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suscripciones_vencimiento ON suscripciones(estado, fecha_fin);
