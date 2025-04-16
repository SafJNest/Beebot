package com.safjnest.springapi.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtFilterConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtSecret);
        registrationBean.setFilter(jwtFilter);
        registrationBean.addUrlPatterns("/api/*");
        
        return registrationBean;
    }
}