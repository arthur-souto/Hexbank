package com.hexbank.shared.adapter.in.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 401/403 do Spring Security acontecem nos filtros, antes do controller: o advice não os vê. */
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(request, response, 401, "Unauthorized", "Autenticação necessária", ErrorCodes.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, 403, "Forbidden", "Acesso negado", ErrorCodes.FORBIDDEN);
    }

    private static void write(HttpServletRequest request, HttpServletResponse response,
                              int status, String title, String detail, String code) throws IOException {
        String requestId = RequestIdFilter.currentRequestId(request);
        String body = "{"
                + "\"type\":\"about:blank\","
                + "\"title\":\"" + title + "\","
                + "\"status\":" + status + ","
                + "\"detail\":\"" + detail + "\","
                + "\"instance\":\"" + escape(request.getRequestURI()) + "\","
                + "\"code\":\"" + code + "\","
                + "\"requestId\":" + (requestId == null ? "null" : "\"" + escape(requestId) + "\"")
                + "}";
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}