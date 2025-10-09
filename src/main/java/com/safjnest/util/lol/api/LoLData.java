package com.safjnest.util.lol.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import com.safjnest.util.lol.api.spring.MatchDTO;
import com.safjnest.util.lol.api.spring.ParticipantDTO;
import com.safjnest.util.lol.api.spring.SummonerDTO;

import java.util.List;
import java.util.Optional;

@Component
public class LoLData {

    private static SummonerRepository summonerRepositoryStatic;
    private static MatchRepository matchRepositoryStatic;
    private static ParticipantRepository participantRepositoryStatic;
    private MatchRepository matchRepository;
    private SummonerRepository summonerRepository;
    private ParticipantRepository participantRepository;

    // Aggiorna il costruttore
    @Autowired
    public LoLData(SummonerRepository summonerRepository,
            MatchRepository matchRepository,
            ParticipantRepository participantRepository) {
        this.summonerRepository = summonerRepository;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
    }

    // Aggiorna il metodo PostConstruct
    @PostConstruct
    private void init() {
        summonerRepositoryStatic = summonerRepository;
        matchRepositoryStatic = matchRepository;
        participantRepositoryStatic = participantRepository;
    }

    public static SummonerDTO findSummonerByPuuid(String puuid) {
        return summonerRepositoryStatic.findByPuuid(puuid).orElse(null);
    }
    
    public static Optional<SummonerDTO> findSummonerWithMasteriesByPuuid(String puuid) {
        return summonerRepositoryStatic.findWithMasteriesByPuuid(puuid);
    }

    // Aggiungi nuovi metodi statici per Match
    public static MatchDTO findMatchById(Integer id) {
        return matchRepositoryStatic.findById(id).orElse(null);
    }

    public static MatchDTO findMatchByGameId(String gameId) {
        return matchRepositoryStatic.findByGameId(gameId).orElse(null);
    }

    public static List<MatchDTO> findMatchesBySummonerPuuid(String puuid, int limit) {
        return matchRepositoryStatic.findBySummonerPuuid(puuid, PageRequest.of(0, limit));
    }

    public static List<MatchDTO> findMatchesBySummonerPuuidAndQueue(String puuid, String queue, int limit) {
        return matchRepositoryStatic.findBySummonerPuuidAndQueue(puuid, queue, PageRequest.of(0, limit));
    }

    // Aggiungi nuovi metodi statici per Participant
    public static ParticipantDTO findParticipantById(Integer id) {
        return participantRepositoryStatic.findById(id).orElse(null);
    }

    public static List<ParticipantDTO> findParticipantsBySummonerPuuid(String puuid, int limit) {
        return participantRepositoryStatic.findBySummonerPuuid(puuid, PageRequest.of(0, limit));
    }

    public static float calculateWinRate(String puuid, int matchCount) {
        List<ParticipantDTO> wins = participantRepositoryStatic.findWinsBySummonerPuuid(puuid,
                PageRequest.of(0, matchCount));
        List<ParticipantDTO> all = participantRepositoryStatic.findBySummonerPuuid(puuid,
                PageRequest.of(0, matchCount));

        if (all.isEmpty())
            return 0f;
        return (float) wins.size() / all.size();
    }
}