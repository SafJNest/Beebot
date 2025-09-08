package com.safjnest.spring.service;

import com.safjnest.spring.entity.*;
import com.safjnest.spring.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer that provides JPA-based equivalents to all LeagueDB SQL query methods.
 * This service converts the raw SQL operations to Spring Data JPA repository calls.
 */
@Service
@Transactional
public class LeagueDataService {

    @Autowired
    private SummonerRepository summonerRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private MasteriesRepository masteriesRepository;

    @Autowired
    private RankRepository rankRepository;

    // ==== SUMMONER OPERATIONS ====

    /**
     * JPA equivalent of: getLOLAccountsByUserId
     */
    public List<Summoner> getLolAccountsByUserId(String userId) {
        return summonerRepository.findByUserIdOrderById(userId);
    }

    /**
     * JPA equivalent of: getUserIdByLOLAccountId
     */
    public Optional<String> getUserIdByLolAccountId(String puuid, Integer leagueShard) {
        return summonerRepository.findUserIdByPuuidAndLeagueShard(puuid, leagueShard);
    }

    /**
     * JPA equivalent of: getFocusedSummoners
     */
    public List<String> getFocusedSummoners(String query, Integer leagueShard) {
        Pageable limit25 = PageRequest.of(0, 25);
        return summonerRepository.findFocusedSummoners(query, leagueShard, limit25);
    }

    /**
     * JPA equivalent of: getSummonerIdByPuuid
     */
    public Optional<Integer> getSummonerIdByPuuid(String puuid, Integer leagueShard) {
        return summonerRepository.findSummonerIdByPuuidAndLeagueShard(puuid, leagueShard);
    }

    /**
     * JPA equivalent of: hasSummonerData
     */
    public boolean hasSummonerData(Integer summonerId) {
        return summonerRepository.hasSummonerData(summonerId);
    }

    /**
     * JPA equivalent of: trackSummoner
     */
    public boolean trackSummoner(String userId, String puuid, boolean track) {
        int tracking = track ? 1 : 0;
        int updated = summonerRepository.updateTracking(userId, puuid, tracking);
        return updated > 0;
    }

    /**
     * JPA equivalent of: deleteLOLaccount
     */
    public boolean deleteLolAccount(String userId, String puuid) {
        int updated = summonerRepository.deleteLolAccount(userId, puuid);
        return updated > 0;
    }

    /**
     * JPA equivalent of: addLOLAccount
     */
    public Summoner addLolAccount(String userId, String riotId, String summonerId, 
                                  String accountId, String puuid, Integer leagueShard) {
        // Check if summoner already exists
        Optional<Summoner> existing = summonerRepository.findByPuuidAndLeagueShard(puuid, leagueShard);
        
        if (existing.isPresent()) {
            // Update existing summoner
            Summoner summoner = existing.get();
            if (userId != null) summoner.setUserId(userId);
            summoner.setSummonerId(summonerId);
            summoner.setAccountId(accountId);
            summoner.setRiotId(riotId);
            return summonerRepository.save(summoner);
        } else {
            // Create new summoner
            Summoner summoner = new Summoner(riotId, summonerId, accountId, puuid, leagueShard, userId);
            return summonerRepository.save(summoner);
        }
    }

    // ==== MATCH OPERATIONS ====

    /**
     * JPA equivalent of: getMatchData
     */
    public List<Object[]> getMatchData() {
        return matchRepository.findMatchDataWithParticipants(10353);
    }

    /**
     * JPA equivalent of: setMatchEvent
     */
    public boolean setMatchEvent(Integer matchId, String json) {
        int updated = matchRepository.updateMatchEvents(matchId, json);
        return updated > 0;
    }

    /**
     * JPA equivalent of: setMatchData
     */
    public Match setMatchData(String gameId, Integer leagueShard, Integer gameType, String bans,
                              LocalDateTime timeStart, LocalDateTime timeEnd, String patch) {
        // Check if match already exists
        Optional<Match> existing = matchRepository.findByGameIdAndLeagueShard(gameId, leagueShard);
        
        if (existing.isPresent()) {
            return existing.get(); // Return existing match
        } else {
            // Create new match
            Match match = new Match(gameId, leagueShard, gameType, bans, timeStart, timeEnd, patch);
            return matchRepository.save(match);
        }
    }

    // ==== PARTICIPANT OPERATIONS ====

    /**
     * JPA equivalent of: getSummonerData - basic version
     */
    public List<Object[]> getSummonerData(Integer summonerId) {
        // Assuming TEAM_BUILDER_RANKED_SOLO ordinal is 43 (based on original query)
        return participantRepository.findSummonerDataByGameType(summonerId, 43);
    }

    /**
     * JPA equivalent of: getSummonerData with game_id
     */
    public List<Object[]> getSummonerData(Integer summonerId, String gameId) {
        return participantRepository.findSummonerDataByGameId(summonerId, gameId);
    }

    /**
     * JPA equivalent of: getSummonerData with time range
     */
    public List<Object[]> getSummonerData(Integer summonerId, Integer leagueShard, 
                                          LocalDateTime timeStart, LocalDateTime timeEnd) {
        return participantRepository.findSummonerDataByTimeRange(summonerId, leagueShard, timeStart, timeEnd);
    }

    /**
     * JPA equivalent of: getAdvancedLOLData - basic version
     */
    public List<Object[]> getAdvancedLolData(Integer summonerId) {
        return participantRepository.findAdvancedLolDataBySummoner(summonerId);
    }

    /**
     * JPA equivalent of: getAdvancedLOLData with filters
     */
    public List<Object[]> getAdvancedLolData(Integer summonerId, LocalDateTime timeStart, 
                                             LocalDateTime timeEnd, Integer gameType) {
        return participantRepository.findAdvancedLolDataWithFilters(summonerId, gameType, timeStart, timeEnd);
    }

    /**
     * JPA equivalent of: getAllGamesForAccount
     */
    public List<Object[]> getAllGamesForAccount(Integer summonerId, LocalDateTime timeStart, LocalDateTime timeEnd) {
        return participantRepository.findAllGamesForAccount(summonerId, timeStart, timeEnd);
    }

    /**
     * JPA equivalent of: getRegistredLolAccount - all registered accounts
     */
    public List<Object[]> getRegisteredLolAccounts(LocalDateTime timeStart) {
        return participantRepository.findRegisteredLolAccountsWithLatestData(timeStart);
    }

    /**
     * JPA equivalent of: getRegistredLolAccount - single summoner
     */
    public List<Object[]> getRegisteredLolAccount(Integer summonerId, LocalDateTime timeStart) {
        return participantRepository.findRegisteredLolAccountWithLatestData(summonerId, timeStart);
    }

    /**
     * JPA equivalent of: setSummonerData
     */
    public Participant setSummonerData(Integer summonerId, Integer matchId, Boolean win, String kda,
                                       Short champion, Byte lane, Byte team, Short rank, Short lp, 
                                       Short gain, String build, String pings) {
        // Check if participant already exists
        if (participantRepository.existsBySummonerIdAndMatchId(summonerId, matchId)) {
            return null; // Equivalent to INSERT IGNORE behavior
        }

        // Create new participant
        Summoner summoner = summonerRepository.findById(summonerId).orElse(null);
        Match match = matchRepository.findById(matchId).orElse(null);
        
        if (summoner == null || match == null) {
            return null;
        }

        Participant participant = new Participant(summoner, match, win, kda);
        participant.setChampion(champion);
        participant.setLane(lane);
        participant.setTeam(team);
        participant.setRank(rank);
        participant.setLp(lp);
        participant.setGain(gain);
        participant.setBuild(build);
        participant.setPings(pings);

        return participantRepository.save(participant);
    }

    // ==== MASTERY OPERATIONS ====

    /**
     * JPA equivalent of: updateSummonerMasteries
     */
    public boolean updateSummonerMasteries(Integer summonerId, List<Masteries> masteriesList) {
        try {
            // First, delete existing masteries for the summoner
            masteriesRepository.deleteBySummonerId(summonerId);
            
            // Save new masteries
            masteriesRepository.saveAll(masteriesList);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==== RANK OPERATIONS ====

    /**
     * JPA equivalent of: updateSummonerEntries
     */
    public boolean updateSummonerEntries(Integer summonerId, List<Rank> ranks) {
        try {
            // Delete existing ranks for the summoner
            rankRepository.deleteBySummonerId(summonerId);
            
            // Save new ranks
            rankRepository.saveAll(ranks);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}