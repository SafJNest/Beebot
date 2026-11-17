package com.safjnest.spring.config;

import java.io.IOException;

import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.spring.exception.ApiErrors;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class AuthFilter implements Filter {

    public static final String AUTH_CONTEXT = "com.safjnest.auth.context";

    @FunctionalInterface
    public interface Verifier {
        AuthContext verify(String key, String bearer, boolean sessionRequired);
    }

    private final Verifier verifier;

    public AuthFilter() {
        this(new DiscordAuthService()::authenticate);
    }

    public AuthFilter(Verifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse result = (HttpServletResponse) response;

        if (!ApiErrors.isBeebot(http)) {
            chain.doFilter(request, response);

            return;
        }

        result.setHeader("Cache-Control", "no-store");

        String path = ApiErrors.path(http);
        String rawPath = http.getRequestURI().substring(http.getContextPath().length());
        if (rawPath.indexOf(';') >= 0 || rawPath.indexOf('\\') >= 0 || rawPath.contains("//") || rawPath.contains("%")) {
            ApiErrors.write(http, result, new Exception(Type.INVALID_REQUEST, "invalid_request", "Invalid request path"));

            return;
        }

        boolean preSession = "POST".equals(http.getMethod())
            && ("/api/beebot/auth/discord/authorize".equals(path) || "/api/beebot/auth/session".equals(path));

        try {
            AuthContext context = verifier.verify(http.getHeader("X-API-Key"), http.getHeader("Authorization"), !preSession);
            http.setAttribute(AUTH_CONTEXT, context);
        } catch (Exception exception) {
            ApiErrors.write(http, result, exception);

            return;
        }

        chain.doFilter(request, response);
    }
}
