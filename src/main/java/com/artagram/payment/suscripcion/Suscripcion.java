package com.artagram.payment.suscripcion;

import com.artagram.payment.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * usuarioId es un UUID plano, sin relación JPA/FK a un Usuario — los
 * usuarios viven en la base de datos de artagram-users, no en esta.
 */
@Entity
@Table(name = "suscripciones")
public class Suscripcion extends BaseEntity {

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 20)
    private String plan; // GRATIS, PRO

    @Column(nullable = false, length = 20)
    private String estado; // ACTIVA, CANCELADA, EXPIRADA

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio = Instant.now();

    @Column(name = "fecha_fin")
    private Instant fechaFin;

    @Column(name = "proveedor_pago", length = 30)
    private String proveedorPago;

    @Column(name = "id_transaccion", length = 100)
    private String idTransaccion;

    public Suscripcion() {
    }

    public Suscripcion(UUID usuarioId, String plan, String estado, Instant fechaInicio, Instant fechaFin,
                        String proveedorPago, String idTransaccion) {
        this.usuarioId = usuarioId;
        this.plan = plan;
        this.estado = estado;
        this.fechaInicio = fechaInicio != null ? fechaInicio : Instant.now();
        this.fechaFin = fechaFin;
        this.proveedorPago = proveedorPago;
        this.idTransaccion = idTransaccion;
    }

    public UUID getUsuarioId() { return usuarioId; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getFechaInicio() { return fechaInicio; }
    public Instant getFechaFin() { return fechaFin; }
    public void setFechaFin(Instant fechaFin) { this.fechaFin = fechaFin; }
    public String getProveedorPago() { return proveedorPago; }
    public void setProveedorPago(String proveedorPago) { this.proveedorPago = proveedorPago; }
    public String getIdTransaccion() { return idTransaccion; }
    public void setIdTransaccion(String idTransaccion) { this.idTransaccion = idTransaccion; }
}
