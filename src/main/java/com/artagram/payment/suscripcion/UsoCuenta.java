package com.artagram.payment.suscripcion;

import com.artagram.payment.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "uso_cuenta")
public class UsoCuenta extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(name = "megabytes_usados", nullable = false)
    private long megabytesUsados = 0;

    @Column(name = "max_megabytes", nullable = false)
    private long maxMegabytes = 512;

    @Column(name = "obras_subidas", nullable = false)
    private int obrasSubidas = 0;

    @Column(name = "max_obras", nullable = false)
    private int maxObras = 50;

    public UsoCuenta() {
    }

    public UsoCuenta(UUID usuarioId, long megabytesUsados, long maxMegabytes, int obrasSubidas, int maxObras) {
        this.usuarioId = usuarioId;
        this.megabytesUsados = megabytesUsados;
        this.maxMegabytes = maxMegabytes;
        this.obrasSubidas = obrasSubidas;
        this.maxObras = maxObras;
    }

    public UUID getUsuarioId() { return usuarioId; }
    public long getMegabytesUsados() { return megabytesUsados; }
    public void setMegabytesUsados(long megabytesUsados) { this.megabytesUsados = megabytesUsados; }
    public long getMaxMegabytes() { return maxMegabytes; }
    public void setMaxMegabytes(long maxMegabytes) { this.maxMegabytes = maxMegabytes; }
    public int getObrasSubidas() { return obrasSubidas; }
    public void setObrasSubidas(int obrasSubidas) { this.obrasSubidas = obrasSubidas; }
    public int getMaxObras() { return maxObras; }
    public void setMaxObras(int maxObras) { this.maxObras = maxObras; }
}
