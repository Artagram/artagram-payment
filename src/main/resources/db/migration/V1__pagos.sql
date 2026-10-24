-- V1__pagos.sql
-- Tablas de suscripciones y uso de cuenta, portadas de artagram-users.
-- Sin FK a usuarios: la tabla usuarios vive en la base de datos de
-- artagram-users, no en esta.

CREATE TABLE IF NOT EXISTS suscripciones (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    plan VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_fin TIMESTAMP,
    proveedor_pago VARCHAR(30),
    id_transaccion VARCHAR(100),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS uso_cuenta (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE,
    megabytes_usados BIGINT NOT NULL DEFAULT 0,
    max_megabytes BIGINT NOT NULL DEFAULT 512,
    obras_subidas INT NOT NULL DEFAULT 0,
    max_obras INT NOT NULL DEFAULT 50,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suscripciones_usuario ON suscripciones(usuario_id, estado);
