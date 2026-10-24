package com.artagram.payment.suscripcion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsoCuentaRepository extends JpaRepository<UsoCuenta, UUID> {
    Optional<UsoCuenta> findByUsuarioId(UUID usuarioId);
    void deleteByUsuarioId(UUID usuarioId);
}
