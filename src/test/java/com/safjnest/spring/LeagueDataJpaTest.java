package com.safjnest.spring;

import com.safjnest.spring.entity.*;
import com.safjnest.spring.repository.*;
import com.safjnest.spring.service.LeagueDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JPA-based League Data implementation.
 * These tests validate that our JPA entities and repositories work correctly
 * as replacements for the raw SQL queries in LeagueDB.
 */
@DataJpaTest
@Import(LeagueDataService.class)
@ActiveProfiles("test")
public class LeagueDataJpaTest {

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

    @Autowired
    private LeagueDataService leagueDataService;

    private Summoner testSummoner;
    private Match testMatch;
    private Participant testParticipant;

    @BeforeEach
    void setUp() {
        // Create test summoner
        testSummoner = new Summoner("TestPlayer#001", "summ123", "acc123", "puuid123", 3, "user123");
        testSummoner = summonerRepository.save(testSummoner);

        // Create test match
        testMatch = new Match("123456789", 3, 43, "{\"100\":[],\"200\":[]}", 
                             LocalDateTime.now().minusHours(1), LocalDateTime.now(), "13.1.1");
        testMatch = matchRepository.save(testMatch);

        // Create test participant
        testParticipant = new Participant(testSummoner, testMatch, true, "5/2/10");
        testParticipant.setChampion((short) 1);
        testParticipant.setLane((byte) 2);
        testParticipant.setTeam((byte) 100);
        testParticipant.setRank((short) 1500);
        testParticipant.setLp((short) 50);
        testParticipant.setGain((short) 18);
        testParticipant.setBuild("{}");
        testParticipant = participantRepository.save(testParticipant);
    }

    @Test
    void testSummonerBasicOperations() {
        // Test finding summoner by PUUID and league shard
        Optional<Summoner> found = summonerRepository.findByPuuidAndLeagueShard("puuid123", 3);
        assertTrue(found.isPresent());
        assertEquals("TestPlayer#001", found.get().getRiotId());

        // Test finding summoner by user ID
        List<Summoner> summoners = summonerRepository.findByUserIdOrderById("user123");
        assertEquals(1, summoners.size());
        assertEquals("puuid123", summoners.get(0).getPuuid());
    }

    @Test
    void testLeagueDataServiceSummonerOperations() {
        // Test getLolAccountsByUserId
        List<Summoner> accounts = leagueDataService.getLolAccountsByUserId("user123");
        assertEquals(1, accounts.size());

        // Test getUserIdByLolAccountId
        Optional<String> userId = leagueDataService.getUserIdByLolAccountId("puuid123", 3);
        assertTrue(userId.isPresent());
        assertEquals("user123", userId.get());

        // Test getSummonerIdByPuuid
        Optional<Integer> summonerId = leagueDataService.getSummonerIdByPuuid("puuid123", 3);
        assertTrue(summonerId.isPresent());
        assertEquals(testSummoner.getId(), summonerId.get());

        // Test hasSummonerData
        assertTrue(leagueDataService.hasSummonerData(testSummoner.getId()));
    }

    @Test
    void testMatchOperations() {
        // Test finding match by game ID and league shard
        Optional<Match> found = matchRepository.findByGameIdAndLeagueShard("123456789", 3);
        assertTrue(found.isPresent());
        assertEquals("13.1.1", found.get().getPatch());

        // Test match exists check
        assertTrue(matchRepository.existsByGameIdAndLeagueShard("123456789", 3));
        assertFalse(matchRepository.existsByGameIdAndLeagueShard("999999999", 3));
    }

    @Test
    void testParticipantQueries() {
        // Test finding participants by summoner
        List<Participant> participants = participantRepository.findBySummonerId(testSummoner.getId());
        assertEquals(1, participants.size());
        assertEquals("5/2/10", participants.get(0).getKda());

        // Test basic summoner data query
        List<Object[]> summonerData = leagueDataService.getSummonerData(testSummoner.getId());
        assertFalse(summonerData.isEmpty());

        // Test advanced LoL data query
        List<Object[]> advancedData = leagueDataService.getAdvancedLolData(testSummoner.getId());
        assertFalse(advancedData.isEmpty());
    }

    @Test
    void testMatchDataQuery() {
        // Test the complex getMatchData query equivalent
        List<Object[]> matchData = leagueDataService.getMatchData();
        // Since our test match has id less than 10353, it shouldn't appear
        assertTrue(matchData.isEmpty());

        // Create a match with higher ID to test the query
        Match highIdMatch = new Match("987654321", 3, 43, "{\"100\":[],\"200\":[]}", 
                                     LocalDateTime.now().minusHours(1), LocalDateTime.now(), "13.1.1");
        // Force a higher ID by persisting multiple matches
        for (int i = 0; i < 20; i++) {
            Match tempMatch = new Match("temp" + i, 3, 43, "{}", 
                                       LocalDateTime.now(), LocalDateTime.now(), "13.1.1");
            matchRepository.save(tempMatch);
        }
        highIdMatch = matchRepository.save(highIdMatch);
        
        // Create participant for high ID match
        Participant highIdParticipant = new Participant(testSummoner, highIdMatch, true, "10/0/15");
        highIdParticipant.setChampion((short) 2);
        highIdParticipant.setBuild("{}");
        participantRepository.save(highIdParticipant);

        matchData = leagueDataService.getMatchData();
        assertTrue(matchData.size() >= 1);
    }

    @Test
    void testMasteryOperations() {
        // Create test mastery
        Masteries mastery = new Masteries(testSummoner, 1, 7, 500000, LocalDateTime.now());
        masteriesRepository.save(mastery);

        // Test finding masteries by summoner
        List<Masteries> masteries = masteriesRepository.findBySummonerId(testSummoner.getId());
        assertEquals(1, masteries.size());
        assertEquals(Integer.valueOf(7), masteries.get(0).getChampionLevel());

        // Test finding by champion
        Optional<Masteries> foundMastery = masteriesRepository.findBySummonerIdAndChampionId(
            testSummoner.getId(), 1);
        assertTrue(foundMastery.isPresent());
        assertEquals(Integer.valueOf(500000), foundMastery.get().getChampionPoints());
    }

    @Test
    void testRankOperations() {
        // Create test rank
        Rank rank = new Rank(testSummoner, 43, 1500, 50, 25, 15);
        rankRepository.save(rank);

        // Test finding ranks by summoner
        List<Rank> ranks = rankRepository.findBySummonerId(testSummoner.getId());
        assertEquals(1, ranks.size());
        assertEquals(Integer.valueOf(1500), ranks.get(0).getRank());

        // Test finding by game type
        Optional<Rank> foundRank = rankRepository.findBySummonerIdAndGameType(testSummoner.getId(), 43);
        assertTrue(foundRank.isPresent());
        assertEquals(Integer.valueOf(50), foundRank.get().getLp());
    }

    @Test
    void testFocusedSummonersQuery() {
        // Test the focused summoners search (equivalent to full-text search)
        List<String> focused = leagueDataService.getFocusedSummoners("TestPlayer", 3);
        assertEquals(1, focused.size());
        assertEquals("TestPlayer#001", focused.get(0));

        // Test with partial match
        focused = leagueDataService.getFocusedSummoners("Test", 3);
        assertEquals(1, focused.size());
    }

    @Test
    void testTrackingOperations() {
        // Test tracking a summoner
        boolean result = leagueDataService.trackSummoner("user123", "puuid123", true);
        assertTrue(result);

        // Verify tracking was updated
        Optional<Summoner> updated = summonerRepository.findByPuuidAndLeagueShard("puuid123", 3);
        assertTrue(updated.isPresent());
        assertEquals(Integer.valueOf(1), updated.get().getTracking());

        // Test untracking
        result = leagueDataService.trackSummoner("user123", "puuid123", false);
        assertTrue(result);

        updated = summonerRepository.findByPuuidAndLeagueShard("puuid123", 3);
        assertTrue(updated.isPresent());
        assertEquals(Integer.valueOf(0), updated.get().getTracking());
    }

    @Test
    void testSetSummonerData() {
        // Create a new match for testing
        Match newMatch = new Match("newmatch123", 3, 43, "{}", 
                                   LocalDateTime.now(), LocalDateTime.now(), "13.1.1");
        newMatch = matchRepository.save(newMatch);

        // Test setting summoner data
        Participant result = leagueDataService.setSummonerData(
            testSummoner.getId(), newMatch.getId(), false, "3/5/8", 
            (short) 2, (byte) 1, (byte) 200, (short) 1400, (short) 45, (short) -12, "{}", "{}"
        );

        assertNotNull(result);
        assertFalse(result.getWin());
        assertEquals("3/5/8", result.getKda());
        assertEquals(Short.valueOf((short) 2), result.getChampion());

        // Test duplicate insertion (should return null due to INSERT IGNORE behavior)
        Participant duplicate = leagueDataService.setSummonerData(
            testSummoner.getId(), newMatch.getId(), true, "10/0/10", 
            (short) 2, (byte) 1, (byte) 200, (short) 1500, (short) 50, (short) 20, "{}", "{}"
        );

        assertNull(duplicate);
    }

    @Test
    void testComplexTimeRangeQueries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime twoHoursAgo = now.minusHours(2);

        // Test getSummonerData with time range
        List<Object[]> timeRangeData = leagueDataService.getSummonerData(
            testSummoner.getId(), 3, twoHoursAgo, now
        );
        assertFalse(timeRangeData.isEmpty());

        // Test getAllGamesForAccount
        List<Object[]> allGames = leagueDataService.getAllGamesForAccount(
            testSummoner.getId(), oneHourAgo, now
        );
        assertFalse(allGames.isEmpty());
    }
}