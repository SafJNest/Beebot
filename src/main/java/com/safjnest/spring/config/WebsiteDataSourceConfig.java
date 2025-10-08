package com.safjnest.spring.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.safjnest.spring.api",
    entityManagerFactoryRef = "websiteEntityManagerFactory",
    transactionManagerRef = "websiteTransactionManager"
)
public class WebsiteDataSourceConfig {

    @Bean(name = "websiteDataSource")
    public DataSource websiteDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Read from spring.properties file - website database configuration
        String websiteUrl = System.getProperty("spring.datasource.website.url", "jdbc:mariadb://localhost:3306/website_dev");
        String websiteUsername = System.getProperty("spring.datasource.website.username", "root");
        String websitePassword = System.getProperty("spring.datasource.website.password", "");
        
        config.setJdbcUrl(websiteUrl);
        config.setUsername(websiteUsername);
        config.setPassword(websitePassword);
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        
        return new HikariDataSource(config);
    }

    @Bean(name = "websiteEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean websiteEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("websiteDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MariaDB106Dialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", true);
        
        return builder
                .dataSource(dataSource)
                .packages("com.safjnest.spring.api.model")
                .persistenceUnit("website")
                .properties(properties)
                .build();
    }

    @Bean(name = "websiteTransactionManager")
    public PlatformTransactionManager websiteTransactionManager(
            @Qualifier("websiteEntityManagerFactory") LocalContainerEntityManagerFactoryBean websiteEntityManagerFactory) {
        return new JpaTransactionManager(websiteEntityManagerFactory.getObject());
    }
}
