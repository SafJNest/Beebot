package com.safjnest.springapi.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.web.filter.OncePerRequestFilter;

import com.safjnest.springapi.service.ApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Not used in the current version, but can be enabled for API key validation.
 * Uncomment the @Component annotation to enable this filter.
 */
//@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${app.api.key.header:X-API-KEY}")
    private String apiKeyHeader;

    @Autowired
    private ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(apiKeyHeader);

        if (apiKey == null || !apiKeyService.isValidApiKey(apiKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key");
            return;
        }

        filterChain.doFilter(request, response);
    }
}