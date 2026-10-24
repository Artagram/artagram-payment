package com.artagram.payment.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Valida el JWT RS256 emitido por artagram-auth. Como el resto de
 * microservicios nuevos, solo recibe tokens RS256: no hace falta un
 * fallback HMAC.
 */
@Service
public final class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final RSAPublicKey publicKey;

    public JwtService(JwtProperties properties) {
        this.publicKey = cargarLlavePublica(properties);
    }

    public Optional<TokenPrincipal> validar(String token) {
        if (publicKey == null) {
            log.warn("Llave pública RSA no disponible: no se puede validar el token");
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(extraerPrincipal(claims));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token inválido o vencido: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static TokenPrincipal extraerPrincipal(Claims claims) {
        UUID usuarioId = UUID.fromString(claims.getSubject());
        String nombreArtista = claims.get("nombreArtista", String.class);
        if (nombreArtista == null || nombreArtista.isBlank()) {
            nombreArtista = claims.get("nombre", String.class);
        }
        String rolGlobal = claims.get("rolGlobal", String.class);
        if (rolGlobal == null || rolGlobal.isBlank()) {
            rolGlobal = claims.get("rol", String.class);
        }
        boolean premium = Boolean.TRUE.equals(claims.get("premium", Boolean.class));
        return new TokenPrincipal(usuarioId, nombreArtista, rolGlobal != null ? rolGlobal : "USUARIO", premium);
    }

    private static RSAPublicKey cargarLlavePublica(JwtProperties props) {
        try {
            String pem = props.getPublicKeyPem();
            if ((pem == null || pem.isBlank()) && props.getPublicKeyPath() != null && !props.getPublicKeyPath().isBlank()) {
                Path p = Path.of(props.getPublicKeyPath());
                if (Files.exists(p)) {
                    pem = Files.readString(p);
                }
            }
            if (pem == null || pem.isBlank()) {
                log.warn("No se encontró llave pública RSA en {}", props.getPublicKeyPath());
                return null;
            }
            String limpio = pem
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(limpio);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            log.warn("No se pudo cargar la llave pública RSA ({})", e.getMessage());
            return null;
        }
    }

    public record TokenPrincipal(UUID usuarioId, String nombreArtista, String rolGlobal, boolean premium) {
    }
}
