package com.safjnest.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.SettingsLoader;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(basePackages = "com.safjnest.spring.api.repository")
@EnableTransactionManagement
public class JpaConfig {

    @Bean
    public DataSource dataSource() {
        // Create a new HikariDataSource with the same configuration as DatabaseHandler
        com.safjnest.model.BotSettings.DatabaseSettings settings = 
            SettingsLoader.getSettings().getJsonSettings().getDatabase();
        
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + settings.getHost() + "/" + 
            SettingsLoader.getSettings().getJsonSettings().getLeagueDatabase().getDatabaseName() + 
            "?autoReconnect=true");
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());
        config.setAutoCommit(true); // JPA manages transactions
        
        config.setMaximumPoolSize(10); // Smaller pool for JPA
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);
        
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan("com.safjnest.spring.api.model");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "none"); // Don't auto-create tables
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");
        properties.setProperty("hibernate.default_catalog", 
            SettingsLoader.getSettings().getJsonSettings().getLeagueDatabase().getDatabaseName());
        
        em.setJpaProperties(properties);
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }
}