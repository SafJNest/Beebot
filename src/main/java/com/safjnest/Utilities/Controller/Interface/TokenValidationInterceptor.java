package com.safjnest.Utilities.Controller.Interface;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TokenValidationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (!isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Figlio di troia non mi distruggi il bot, beebot its too safe to get hacked");
            return false;
        }
        return true;
    }

    private boolean isValidToken(String token) {
        if (token.equals("Bearer 123")) {
            return true;
        }
        return false;
    }
}