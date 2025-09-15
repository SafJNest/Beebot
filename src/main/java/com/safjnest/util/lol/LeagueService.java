package com.safjnest.util.lol;

import com.safjnest.util.lol.entity.SummonerDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class LeagueService {

    private static LeagueService instance;

    private final SummonerRepository summonerRepository;

    @Autowired
    public LeagueService(SummonerRepository summonerRepository) {
        this.summonerRepository = summonerRepository;
    }

    @PostConstruct
    private void init() {
        instance = this;
    }

    public static SummonerDTO getSummonerById(Integer id) {
        return instance.summonerRepository.findById(id).orElse(null);
    }

    public static SummonerDTO getSummonerByPuuid(String puuid) {
        return instance.summonerRepository.findByPuuid(puuid).orElse(null);
    }
}
