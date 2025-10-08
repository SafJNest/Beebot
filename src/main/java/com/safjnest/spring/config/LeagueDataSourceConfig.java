package com.safjnest.spring.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    basePackages = "com.safjnest.util.lol.api",
    entityManagerFactoryRef = "leagueEntityManagerFactory",
    transactionManagerRef = "leagueTransactionManager"
)
public class LeagueDataSourceConfig {

    @Primary
    @Bean(name = "leagueDataSource")
    public DataSource leagueDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Read from spring.properties file
        String url = System.getProperty("spring.datasource.url", "jdbc:mariadb://localhost:3306/league_of_legends");
        String username = System.getProperty("spring.datasource.username", "root");
        String password = System.getProperty("spring.datasource.password", "");
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        
        return new HikariDataSource(config);
    }

    @Primary
    @Bean(name = "leagueEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean leagueEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("leagueDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MariaDB106Dialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", true);
        
        return builder
                .dataSource(dataSource)
                .packages("com.safjnest.util.lol.api.spring")
                .persistenceUnit("league")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "leagueTransactionManager")
    public PlatformTransactionManager leagueTransactionManager(
            @Qualifier("leagueEntityManagerFactory") LocalContainerEntityManagerFactoryBean leagueEntityManagerFactory) {
        return new JpaTransactionManager(leagueEntityManagerFactory.getObject());
    }
}
