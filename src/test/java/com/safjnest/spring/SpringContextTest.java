package com.safjnest.spring;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.safjnest.spring.service.GuildService;
import com.safjnest.spring.service.MemberService;
import com.safjnest.spring.service.SoundService;

/**
 * Basic test to verify Spring context loads and services are available
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class SpringContextTest {

    @Autowired
    private GuildService guildService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private SoundService soundService;

    @Test
    public void contextLoads() {
        assertNotNull(guildService, "GuildService should be autowired");
        assertNotNull(memberService, "MemberService should be autowired");
        assertNotNull(soundService, "SoundService should be autowired");
    }

    @Test
    public void servicesAreOperational() {
        // Basic test to ensure services can be called without errors
        assertDoesNotThrow(() -> {
            guildService.getGuild("test_guild_id");
        }, "GuildService should not throw exceptions on basic calls");
    }
}