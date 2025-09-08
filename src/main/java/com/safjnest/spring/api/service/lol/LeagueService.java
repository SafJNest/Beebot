package com.safjnest.spring.api.service.lol;

import com.safjnest.spring.api.model.lol.*;
import com.safjnest.spring.api.repository.lol.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LeagueService {

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

    // Summoner methods
    public List<Summoner> getLOLAccountsByUserId(String userId) {
        return summonerRepository.findByUserIdOrderById(userId);
    }

    public Optional<String> getUserIdByLOLAccountId(String puuid, Integer leagueShard) {
        return summonerRepository.findByPuuidAndLeagueShard(puuid, leagueShard)
                .map(Summoner::getUserId);
    }

    public List<Object[]> getAdvancedLOLData(Integer summonerId) {
        return participantRepository.getAdvancedLOLData(summonerId);
    }

    public List<Object[]> getAdvancedLOLData(Integer summonerId, LocalDateTime timeStart, LocalDateTime timeEnd, Integer gameType) {
        return participantRepository.getAdvancedLOLDataWithFilters(summonerId, timeStart, timeEnd, gameType);
    }

    public List<Object[]> getAllGamesForAccount(Integer summonerId, LocalDateTime timeStart, LocalDateTime timeEnd) {
        return participantRepository.getAllGamesForAccount(summonerId, timeStart, timeEnd);
    }

    public Integer addLOLAccount(String userId, String riotId, String summonerId, String accountId, String puuid, Integer leagueShard) {
        Optional<Summoner> existingSummoner = summonerRepository.findByPuuidAndLeagueShard(puuid, leagueShard);
        
        if (existingSummoner.isPresent()) {
            Summoner summoner = existingSummoner.get();
            summoner.setUserId(userId);
            summoner.setRiotId(riotId);
            summoner.setSummonerId(summonerId);
            summoner.setAccountId(accountId);
            return summonerRepository.save(summoner).getId();
        } else {
            Summoner newSummoner = new Summoner(riotId, summonerId, accountId, puuid, leagueShard, userId, 0);
            return summonerRepository.save(newSummoner).getId();
        }
    }

    public boolean addLOLAccountFromSpectator(String riotId, String summonerId, String puuid, Integer leagueShard) {
        try {
            Optional<Summoner> existingSummoner = summonerRepository.findByPuuidAndLeagueShard(puuid, leagueShard);
            
            if (existingSummoner.isPresent()) {
                Summoner summoner = existingSummoner.get();
                summoner.setRiotId(riotId);
                summoner.setSummonerId(summonerId);
                summonerRepository.save(summoner);
            } else {
                Summoner newSummoner = new Summoner(riotId, summonerId, null, puuid, leagueShard, null, 0);
                summonerRepository.save(newSummoner);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteLOLAccount(String userId, String puuid) {
        try {
            List<Summoner> summoners = summonerRepository.findByPuuid(puuid);
            for (Summoner summoner : summoners) {
                if (userId.equals(summoner.getUserId())) {
                    summoner.setTracking(0);
                    summoner.setUserId(null);
                    summonerRepository.save(summoner);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Summoner> getFocusedSummoners(String query, Integer leagueShard) {
        return summonerRepository.findFocusedSummoners(query, leagueShard);
    }

    public Optional<Participant> getSummonerData(Integer summonerId, String gameId) {
        return participantRepository.findBySummonerIdAndGameId(summonerId, gameId);
    }

    public List<Participant> getSummonerData(Integer summonerId, Integer leagueShard, LocalDateTime timeStart, LocalDateTime timeEnd) {
        return participantRepository.findBySummonerIdAndTimeRange(summonerId, leagueShard, timeStart, timeEnd);
    }

    public List<Participant> getSummonerData(Integer summonerId) {
        return participantRepository.findBySummonerId(summonerId);
    }

    public boolean hasSummonerData(Integer summonerId) {
        return participantRepository.existsBySummonerId(summonerId);
    }

    public boolean trackSummoner(String userId, String accountId, boolean track) {
        try {
            List<Summoner> summoners = summonerRepository.findByUserIdOrderById(userId);
            for (Summoner summoner : summoners) {
                if (accountId.equals(summoner.getAccountId())) {
                    summoner.setTracking(track ? 1 : 0);
                    summonerRepository.save(summoner);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<Summoner> getSummonerData(String userId, String accountId) {
        List<Summoner> summoners = summonerRepository.findByUserIdOrderById(userId);
        return summoners.stream()
                .filter(s -> accountId.equals(s.getAccountId()))
                .findFirst();
    }

    public List<Summoner> getSummonersByPuuid(String puuid) {
        return summonerRepository.findByPuuid(puuid);
    }

    public Optional<Integer> getSummonerIdByPuuid(String puuid, Integer leagueShard) {
        return summonerRepository.findByPuuidAndLeagueShard(puuid, leagueShard)
                .map(Summoner::getId);
    }

    // Match methods
    public Match setMatchData(String gameId, Integer leagueShard, Integer gameType, String bans, LocalDateTime timeStart, LocalDateTime timeEnd, String patch) {
        Optional<Match> existingMatch = matchRepository.findByGameId(gameId);
        
        if (existingMatch.isPresent()) {
            return existingMatch.get();
        } else {
            Match newMatch = new Match(gameId, leagueShard, gameType, bans, timeStart, timeEnd, patch);
            return matchRepository.save(newMatch);
        }
    }

    public boolean setMatchEvent(Integer matchId, String json) {
        try {
            Optional<Match> match = matchRepository.findById(matchId);
            if (match.isPresent()) {
                match.get().setEvents(json);
                matchRepository.save(match.get());
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Match> getMatchData() {
        return matchRepository.findAll();
    }

    // Participant methods
    public boolean setSummonerData(Integer summonerId, Integer matchId, Boolean win, String kda, Short champion, 
                                  Byte lane, Byte team, Short rank, Short lp, Short gain, Integer damage, 
                                  Integer damageBuilding, Integer healing, Short cs, Integer goldEarned, 
                                  Short ward, Short wardKilled, Short visionScore, String pings, String build) {
        try {
            Optional<Participant> existingParticipant = participantRepository.findBySummonerIdAndMatchId(summonerId, matchId);
            
            if (existingParticipant.isPresent()) {
                return true; // Already exists
            }

            Participant participant = new Participant();
            participant.setSummoner(summonerRepository.findById(summonerId).orElse(null));
            participant.setMatch(matchRepository.findById(matchId).orElse(null));
            participant.setWin(win);
            participant.setKda(kda);
            participant.setChampion(champion);
            participant.setLane(lane);
            participant.setTeam(team);
            participant.setRank(rank);
            participant.setLp(lp);
            participant.setGain(gain);
            participant.setDamage(damage);
            participant.setDamageBuilding(damageBuilding);
            participant.setHealing(healing);
            participant.setCs(cs);
            participant.setGoldEarned(goldEarned);
            participant.setWard(ward);
            participant.setWardKilled(wardKilled);
            participant.setVisionScore(visionScore);
            participant.setPings(pings);
            participant.setBuild(build);
            
            participantRepository.save(participant);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Masteries methods
    public boolean updateSummonerMasteries(Integer summonerId, List<Object[]> masteriesData) {
        try {
            // Delete existing masteries
            masteriesRepository.deleteBySummonerId(summonerId);
            
            // Add new masteries
            Optional<Summoner> summoner = summonerRepository.findById(summonerId);
            if (summoner.isPresent()) {
                for (Object[] data : masteriesData) {
                    Masteries mastery = new Masteries();
                    mastery.setSummoner(summoner.get());
                    mastery.setChampionId((Integer) data[0]);
                    mastery.setChampionLevel((Integer) data[1]);
                    mastery.setChampionPoints((Integer) data[2]);
                    mastery.setLastPlayTime((LocalDateTime) data[3]);
                    masteriesRepository.save(mastery);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}