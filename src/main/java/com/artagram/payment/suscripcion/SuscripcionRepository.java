package com.artagram.payment.suscripcion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, UUID> {
    Optional<Suscripcion> findTopByUsuarioIdOrderByFechaInicioDesc(UUID usuarioId);
    List<Suscripcion> findByEstadoAndFechaFinBefore(String estado, Instant limite);
    void deleteAllByUsuarioId(UUID usuarioId);
}
