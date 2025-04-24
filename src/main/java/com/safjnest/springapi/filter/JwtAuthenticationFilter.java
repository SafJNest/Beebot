package com.safjnest.springapi.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey jwtKey;

    public JwtAuthenticationFilter(String jwtSecret) {
        this.jwtKey = getSignInKey(jwtSecret);
    }

    private SecretKey getSignInKey(String jwtSecret) {
        byte[] bytes = Base64.getUrlDecoder().decode(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private List<SimpleGrantedAuthority> getAuthorities(Claims claims) {
        Object scopesObj = claims.get("scopes");
        List<String> scopes;
        if (scopesObj instanceof List<?> rawList) {
            scopes = rawList.stream()
                            .filter(item -> item instanceof String)
                            .map(item -> (String) item)
                            .toList();
        } else {
            scopes = Collections.emptyList();
        }
        List<SimpleGrantedAuthority> authorities = scopes == null ? 
            Collections.emptyList() :
            scopes.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authorities;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                .verifyWith(jwtKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), 
                        null, 
                        getAuthorities(claims));

                SecurityContextHolder.getContext().setAuthentication(auth);
                
            } catch (JwtException e) {
                System.out.println("Invalid JWT token: " + e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Authorization header");
            return;
        }
        filterChain.doFilter(request, response);
    }
}