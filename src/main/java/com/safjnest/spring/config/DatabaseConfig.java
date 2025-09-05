package com.safjnest.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.safjnest.model.BotSettings.DatabaseSettings;
import com.safjnest.util.SettingsLoader;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.safjnest.spring.repository")
@EntityScan(basePackages = "com.safjnest.spring.entity")
@EnableTransactionManagement
public class DatabaseConfig {
    
    @Bean
    public DataSource dataSource() {
        DatabaseSettings settings = SettingsLoader.getSettings().getJsonSettings().getDatabase();
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + settings.getHost() + "/" + settings.getDatabaseName() + "?autoReconnect=true");
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        
        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(true);
        
        return new HikariDataSource(config);
    }
}