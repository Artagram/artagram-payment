package com.artagram.payment.auth;

import com.artagram.payment.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resuelve el usuario autenticado en un endpoint REST a partir del header Authorization. */
@Component
public class JwtUserResolver {

    private final JwtService jwtService;

    public JwtUserResolver(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Optional<JwtService.TokenPrincipal> resolverOpcional(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return jwtService.validar(authHeader.substring(7));
    }

    public JwtService.TokenPrincipal resolverRequerido(HttpServletRequest request) {
        return resolverOpcional(request)
                .orElseThrow(() -> ApiException.unauthorized("NO_AUTENTICADO", "Se requiere token de autenticación"));
    }
}
