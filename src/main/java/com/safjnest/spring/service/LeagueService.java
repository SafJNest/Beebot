package com.safjnest.spring.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.safjnest.spring.entity.SummonerEntity;
import com.safjnest.spring.repository.SummonerRepository;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@Service
@Transactional
public class LeagueService {

    @Autowired
    private SummonerRepository summonerRepository;

    @Cacheable(value = "summoners", key = "#puuid + ':' + #shard")
    public SummonerEntity getSummoner(String puuid, LeagueShard shard) {
        return summonerRepository.findByPuuidAndLeagueShard(puuid, shard.ordinal()).orElse(null);
    }

    public List<SummonerEntity> getSummonersByUser(String userId) {
        return summonerRepository.findByUserId(userId);
    }

    @CacheEvict(value = "summoners", key = "#summoner.puuid + ':' + #summoner.leagueShard")
    public SummonerEntity saveSummoner(SummonerEntity summoner) {
        return summonerRepository.save(summoner);
    }

    public List<SummonerEntity> getTrackedSummoners() {
        return summonerRepository.findByTrackingTrue();
    }

    public List<SummonerEntity> searchSummoners(String query, LeagueShard shard) {
        return summonerRepository.searchByRiotIdAndShard(query, shard.ordinal());
    }

    @CacheEvict(value = "summoners", key = "#puuid + ':' + #shard.ordinal()")
    public boolean updateTracking(String puuid, LeagueShard shard, boolean tracking) {
        Optional<SummonerEntity> summoner = summonerRepository.findByPuuidAndLeagueShard(puuid, shard.ordinal());
        if (summoner.isPresent()) {
            SummonerEntity entity = summoner.get();
            entity.setTracking(tracking);
            summonerRepository.save(entity);
            return true;
        }
        return false;
    }
}