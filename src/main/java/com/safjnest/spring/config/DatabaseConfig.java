package com.safjnest.spring.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.util.SettingsLoader;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        DatabaseSettings settings = SettingsLoader.getSettings().getJsonSettings().getDatabase();
        
        return DataSourceBuilder.create()
                .driverClassName("org.mariadb.jdbc.Driver")
                .url("jdbc:mariadb://" + settings.getHost() + "/" + settings.getDatabaseName() + "?autoReconnect=true")
                .username(settings.getUsername())
                .password(settings.getPassword())
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.league")
    public DataSource leagueDataSource() {
        DatabaseSettings settings = SettingsLoader.getSettings().getJsonSettings().getLeagueDatabase();
        
        return DataSourceBuilder.create()
                .driverClassName("org.mariadb.jdbc.Driver")
                .url("jdbc:mariadb://" + settings.getHost() + "/" + settings.getDatabaseName() + "?autoReconnect=true")
                .username(settings.getUsername())
                .password(settings.getPassword())
                .build();
    }
}