package com.safjnest.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Basic test to verify Spring context loads with new JPA configuration
 */
@SpringBootTest(classes = com.safjnest.App.class)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.show-sql=false"
})
public class LeagueJpaConfigTest {

    @Test
    public void contextLoads() {
        // This test verifies that the Spring context can load with our new JPA configuration
        // The test will fail if there are configuration issues
    }
}