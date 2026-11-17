package com.artagram.payment.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.artagram.payment.common.error.ApiException;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtServiceTest {

    private static KeyPair par;
    private static String pem;

    @TempDir
    Path dir;

    @BeforeAll
    static void llaves() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        par = gen.generateKeyPair();
        pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(par.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    static String token(UUID id, String claimNombre, String nombre, String claimRol, String rol, Boolean premium) {
        var b = Jwts.builder().subject(id.toString()).expiration(new Date(System.currentTimeMillis() + 60_000));
        if (nombre != null) b.claim(claimNombre, nombre);
        if (rol != null) b.claim(claimRol, rol);
        if (premium != null) b.claim("premium", premium);
        return b.signWith(par.getPrivate()).compact();
    }

    static JwtService servicio(String pemInline, String path) {
        JwtProperties props = new JwtProperties();
        props.setPublicKeyPem(pemInline);
        props.setPublicKeyPath(path);
        return new JwtService(props);
    }

    static JwtService servicioValido() {
        return servicio(pem, "");
    }

    @Test
    void validaTokensConClaimsPrincipalesYAlternativos() {
        JwtService jwt = servicioValido();
        UUID id = UUID.randomUUID();

        var principal = jwt.validar(token(id, "nombreArtista", "lucia", "rolGlobal", "ADMIN", true)).orElseThrow();
        assertThat(principal).isEqualTo(new JwtService.TokenPrincipal(id, "lucia", "ADMIN", true));

        var alterno = jwt.validar(token(id, "nombre", "sam", "rol", "MOD", null)).orElseThrow();
        assertThat(alterno.nombreArtista()).isEqualTo("sam");
        assertThat(alterno.rolGlobal()).isEqualTo("MOD");
        assertThat(alterno.premium()).isFalse();

        var vacio = jwt.validar(token(id, "x", null, "y", null, false)).orElseThrow();
        assertThat(vacio.rolGlobal()).isEqualTo("USUARIO");
        assertThat(jwt.validar("basura")).isEmpty();
    }

    @Test
    void cargaLaLlaveDesdeArchivoOQuedaSinLlave() throws Exception {
        Path archivo = Files.writeString(dir.resolve("pub.pem"), pem);
        String t = token(UUID.randomUUID(), "nombreArtista", "a", "rolGlobal", "USUARIO", false);

        assertThat(servicio("", archivo.toString()).validar(t)).isPresent();
        assertThat(servicio("", dir.resolve("no.pem").toString()).validar(t)).isEmpty();
        assertThat(servicio(null, null).validar(t)).isEmpty();
        assertThat(servicio("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----", "").validar(t)).isEmpty();
        assertThat(new JwtProperties().getPublicKeyPath()).endsWith("jwt-public.pem");
    }

    @Test
    void elResolverExigeBearerValido() {
        JwtUserResolver resolver = new JwtUserResolver(servicioValido());
        UUID id = UUID.randomUUID();

        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("Authorization", "Bearer " + token(id, "nombreArtista", "a", "rolGlobal", "USUARIO", false));
        assertThat(resolver.resolverRequerido(ok).usuarioId()).isEqualTo(id);

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic xyz");
        assertThat(resolver.resolverOpcional(basic)).isEmpty();
        assertThatThrownBy(() -> resolver.resolverRequerido(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NO_AUTENTICADO");
    }
}
