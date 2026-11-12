package com.safjnest.spring.config;

import com.safjnest.auth.DiscordAuthService.AuthContext;
import java.io.IOException;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordAuthService;
import com.safjnest.spring.controller.AuthExceptionHandler.AuthError;
import com.safjnest.utils.JsonCodec;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

public final class BeebotAuthFilter implements Filter {
    @FunctionalInterface
    public interface Verifier { AuthContext verify(String key, String bearer, boolean sessionRequired); }
    public static final String AUTH_CONTEXT = "com.safjnest.auth.context";
    private final Verifier verifier;

    public BeebotAuthFilter() { this(new DiscordAuthService()::authenticate); }
    public BeebotAuthFilter(Verifier verifier) { this.verifier = verifier; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse result = (HttpServletResponse) response;
        String includedPath = (String) http.getAttribute("jakarta.servlet.include.servlet_path");
        String path = includedPath == null ? http.getServletPath() : includedPath;
        if (!path.equals("/api/beebot") && !path.startsWith("/api/beebot/")) { chain.doFilter(request, response); return; }
        result.setHeader("Cache-Control", "no-store");
        String rawPath = http.getRequestURI().substring(http.getContextPath().length());
        if (rawPath.indexOf(';') >= 0 || rawPath.indexOf('\\') >= 0 || rawPath.contains("//") || rawPath.contains("%")) {
            error(result, HttpStatus.BAD_REQUEST.value(), "invalid_request", "Invalid request path");
            return;
        }
        boolean preSession = "POST".equals(http.getMethod())
            && ("/api/beebot/auth/discord/authorize".equals(path) || "/api/beebot/auth/session".equals(path));
        try { http.setAttribute(AUTH_CONTEXT, verifier.verify(http.getHeader("X-API-Key"), http.getHeader("Authorization"), !preSession)); }
        catch (AuthException exception) {
            error(result, exception.getStatusCode().value(), exception.code(), exception.getReason());
            return;
        }
        chain.doFilter(request, response);
    }

    private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(JsonCodec.toJson(new AuthError(code, message)));
    }
}
