package com.ticketwar.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAccessFilter implements WebFilter {

    private final String adminUsername = System.getenv("TICKET_WAR_ADMIN_USERNAME");
    private final String adminPassword = System.getenv("TICKET_WAR_ADMIN_PASSWORD");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiresAdminAccess(exchange)) {
            return chain.filter(exchange);
        }

        if (isMissing(adminUsername) || isMissing(adminPassword)) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin sign-in is not configured for this service.", false);
        }

        if (!hasValidCredentials(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Admin sign-in is required.", true);
        }

        return chain.filter(exchange);
    }

    private static boolean requiresAdminAccess(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        if ("/api/admin/auth/session".equals(path)) return true;

        boolean eventPath = "/api/events".equals(path) || path.startsWith("/api/events/");
        if (eventPath) return isWriteMethod(method);

        if (!"/api/war-sessions".equals(path) && !path.startsWith("/api/war-sessions/")) return false;
        if ("GET".equalsIgnoreCase(method) && "/api/war-sessions".equals(path)
                && "SCHEDULED".equalsIgnoreCase(
                        exchange.getRequest().getQueryParams().getFirst("status"))) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method)
                && path.matches("^/api/war-sessions/[^/]+/participants/(register|access-link)$")) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("^/api/war-sessions/[^/]+$")) {
            return false;
        }
        return true;
    }

    private static boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private boolean hasValidCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(6).trim());
            int separator = -1;
            for (int index = 0; index < decoded.length; index++) {
                if (decoded[index] == ':') {
                    separator = index;
                    break;
                }
            }
            if (separator < 0) return false;

            String username = new String(decoded, 0, separator, StandardCharsets.UTF_8);
            String password = new String(decoded, separator + 1, decoded.length - separator - 1, StandardCharsets.UTF_8);
            boolean usernameMatches = MessageDigest.isEqual(
                    username.getBytes(StandardCharsets.UTF_8), adminUsername.getBytes(StandardCharsets.UTF_8));
            boolean passwordMatches = MessageDigest.isEqual(
                    password.getBytes(StandardCharsets.UTF_8), adminPassword.getBytes(StandardCharsets.UTF_8));
            return usernameMatches & passwordMatches;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message, boolean challenge) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (challenge) {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Ticket War Admin\"");
        }
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
