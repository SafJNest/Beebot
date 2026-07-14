package com.safjnest.spring.config;

import java.util.List;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.safjnest.spring")
public class LolApiConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.addMixIn(ChampionStatistics.class, FilterApiMixin.class);
        mapper.addMixIn(Build.class, FilterApiMixin.class);
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
    }

    @JsonIgnoreProperties("filter")
    private abstract static class FilterApiMixin {}

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/lol/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*");
    }
}
