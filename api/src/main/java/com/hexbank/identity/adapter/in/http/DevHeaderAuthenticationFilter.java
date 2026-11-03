package com.hexbank.identity.adapter.in.http;

import com.hexbank.identity.domain.PrincipalId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Identidade SIMULADA, só em dev/test.
 * X-Dev-Principal: <uuid>          autentica
 * X-Dev-Roles: TEST_ADMIN,OUTRA    opcional, vira ROLE_TEST_ADMIN...
 * Header ausente ou inválido = anônimo (401). Será trocado por JWT.
 */
public class DevHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_HEADER = "X-Dev-Principal";
    public static final String ROLES_HEADER = "X-Dev-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        parsePrincipal(request.getHeader(PRINCIPAL_HEADER)).ifPresent(principal -> {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, rolesFrom(request.getHeader(ROLES_HEADER)));

            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
        });
        // Sem principal válido segue anônimo: o entry point devolve 401 problem+json se a rota exigir login.
        chain.doFilter(request, response);
    }

    private static Optional<PrincipalId> parsePrincipal(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PrincipalId.of(raw.trim()));
        } catch (IllegalArgumentException invalidUuid) {
            return Optional.empty();
        }
    }

    private static List<SimpleGrantedAuthority> rolesFrom(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}