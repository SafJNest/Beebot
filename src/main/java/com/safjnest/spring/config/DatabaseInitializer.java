package com.safjnest.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.safjnest.spring.repository.GuildRepository;
import com.safjnest.spring.repository.SoundRepository;
import com.safjnest.spring.repository.MemberRepository;
import com.safjnest.util.log.BotLogger;

/**
 * Database initialization component that runs after Spring Boot startup.
 * Can be used to perform database setup or migration tasks.
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {
    
    private final GuildRepository guildRepository;
    private final SoundRepository soundRepository;
    private final MemberRepository memberRepository;
    
    @Autowired
    public DatabaseInitializer(GuildRepository guildRepository, 
                              SoundRepository soundRepository,
                              MemberRepository memberRepository) {
        this.guildRepository = guildRepository;
        this.soundRepository = soundRepository;
        this.memberRepository = memberRepository;
    }
    
    @Override
    public void run(String... args) throws Exception {
        BotLogger.info("Database initialized successfully!");
        BotLogger.info("Total guilds: " + guildRepository.count());
        BotLogger.info("Total sounds: " + soundRepository.count());
        BotLogger.info("Total members: " + memberRepository.count());
    }
}