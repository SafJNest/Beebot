package com.safjnest.util.lol.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import com.safjnest.util.lol.api.spring.SummonerDTO;

import java.util.Optional;

@Component
public class LoLData {
    
    private static SummonerRepository summonerRepositoryStatic;
    
    private final SummonerRepository summonerRepository;
    
    @Autowired
    public LoLData(SummonerRepository summonerRepository) {
        this.summonerRepository = summonerRepository;
    }
    
    @PostConstruct
    private void init() {
        summonerRepositoryStatic = summonerRepository;
    }
    
    /**
     * Static methods for direct data access
     */
    public static SummonerDTO findSummonerByPuuid(String puuid) {
        return summonerRepositoryStatic.findByPuuid(puuid).orElse(null);
    }
    
    public static Optional<SummonerDTO> findSummonerWithMasteriesByPuuid(String puuid) {
        return summonerRepositoryStatic.findWithMasteriesByPuuid(puuid);
    }
    
    // Add more convenient static methods as needed
}