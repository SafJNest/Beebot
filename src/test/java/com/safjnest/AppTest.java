package com.safjnest;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDateTime;

import org.junit.Test;

import com.safjnest.util.lol.entity.SummonerDTO;
import com.safjnest.util.lol.entity.MatchDTO;
import com.safjnest.util.lol.entity.ParticipantDTO;
import com.safjnest.util.lol.entity.RankDTO;
import com.safjnest.util.lol.entity.MasteryDTO;

/**
 * Unit test for League of Legends DTOs
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }

    /**
     * Test SummonerDTO creation and getters/setters
     */
    @Test
    public void testSummonerDTOCreation() {
        SummonerDTO summoner = new SummonerDTO();
        summoner.setId(1);
        summoner.setRiotId("TestUser#123");
        summoner.setSummonerId("testSummonerId");
        summoner.setAccountId("testAccountId");
        summoner.setPuuid("test-puuid-12345");
        summoner.setLeagueShard(3);
        summoner.setUserId("testUserId");
        summoner.setTracking(1);
        
        assertEquals(Integer.valueOf(1), summoner.getId());
        assertEquals("TestUser#123", summoner.getRiotId());
        assertEquals("testSummonerId", summoner.getSummonerId());
        assertEquals("testAccountId", summoner.getAccountId());
        assertEquals("test-puuid-12345", summoner.getPuuid());
        assertEquals(Integer.valueOf(3), summoner.getLeagueShard());
        assertEquals("testUserId", summoner.getUserId());
        assertEquals(Integer.valueOf(1), summoner.getTracking());
    }

    /**
     * Test MatchDTO creation and getters/setters
     */
    @Test
    public void testMatchDTOCreation() {
        MatchDTO match = new MatchDTO();
        LocalDateTime now = LocalDateTime.now();
        
        match.setId(1);
        match.setGameId("test-game-id");
        match.setLeagueShard(3);
        match.setGameType(420);
        match.setBans("[]");
        match.setTimeStart(now.minusHours(1));
        match.setTimeEnd(now);
        match.setPatch("14.21");
        
        assertEquals(Integer.valueOf(1), match.getId());
        assertEquals("test-game-id", match.getGameId());
        assertEquals(Integer.valueOf(3), match.getLeagueShard());
        assertEquals(Integer.valueOf(420), match.getGameType());
        assertEquals("[]", match.getBans());
        assertNotNull(match.getTimeStart());
        assertNotNull(match.getTimeEnd());
        assertEquals("14.21", match.getPatch());
    }

    /**
     * Test ParticipantDTO creation and getters/setters
     */
    @Test
    public void testParticipantDTOCreation() {
        ParticipantDTO participant = new ParticipantDTO();
        
        participant.setId(1);
        participant.setWin(true);
        participant.setKda("10/2/5");
        participant.setChampion((short) 103);
        participant.setLane((byte) 2);
        participant.setTeam((byte) 100);
        participant.setRank((short) 1200);
        participant.setLp((short) 50);
        participant.setGain((short) 20);
        participant.setDamage(25000);
        participant.setCs((short) 180);
        participant.setGoldEarned(15000);
        participant.setWard((short) 15);
        participant.setVisionScore((short) 45);
        participant.setPings("{}");
        participant.setBuild("{}");
        
        assertEquals(Integer.valueOf(1), participant.getId());
        assertEquals(Boolean.TRUE, participant.getWin());
        assertEquals("10/2/5", participant.getKda());
        assertEquals(Short.valueOf((short) 103), participant.getChampion());
        assertEquals(Byte.valueOf((byte) 2), participant.getLane());
        assertEquals(Byte.valueOf((byte) 100), participant.getTeam());
        assertEquals(Short.valueOf((short) 1200), participant.getRank());
        assertEquals(Short.valueOf((short) 50), participant.getLp());
        assertEquals(Short.valueOf((short) 20), participant.getGain());
        assertEquals(Integer.valueOf(25000), participant.getDamage());
        assertEquals(Short.valueOf((short) 180), participant.getCs());
        assertEquals(Integer.valueOf(15000), participant.getGoldEarned());
        assertEquals(Short.valueOf((short) 15), participant.getWard());
        assertEquals(Short.valueOf((short) 45), participant.getVisionScore());
        assertEquals("{}", participant.getPings());
        assertEquals("{}", participant.getBuild());
    }

    /**
     * Test RankDTO creation and getters/setters
     */
    @Test
    public void testRankDTOCreation() {
        RankDTO rank = new RankDTO();
        LocalDateTime now = LocalDateTime.now();
        
        rank.setId(1);
        rank.setGameType(420);
        rank.setRank(1200);
        rank.setLp(50);
        rank.setWins(25);
        rank.setLosses(15);
        rank.setLastUpdate(now);
        
        assertEquals(Integer.valueOf(1), rank.getId());
        assertEquals(Integer.valueOf(420), rank.getGameType());
        assertEquals(Integer.valueOf(1200), rank.getRank());
        assertEquals(Integer.valueOf(50), rank.getLp());
        assertEquals(Integer.valueOf(25), rank.getWins());
        assertEquals(Integer.valueOf(15), rank.getLosses());
        assertEquals(now, rank.getLastUpdate());
    }

    /**
     * Test MasteryDTO creation and getters/setters
     */
    @Test
    public void testMasteryDTOCreation() {
        MasteryDTO mastery = new MasteryDTO();
        LocalDateTime now = LocalDateTime.now();
        
        mastery.setId(1);
        mastery.setChampionId(103);
        mastery.setChampionLevel(7);
        mastery.setChampionPoints(150000);
        mastery.setLastPlayTime(now);
        
        assertEquals(Integer.valueOf(1), mastery.getId());
        assertEquals(Integer.valueOf(103), mastery.getChampionId());
        assertEquals(Integer.valueOf(7), mastery.getChampionLevel());
        assertEquals(Integer.valueOf(150000), mastery.getChampionPoints());
        assertEquals(now, mastery.getLastPlayTime());
    }

    /**
     * Test Default Values in DTOs
     */
    @Test
    public void testDTODefaultValues() {
        SummonerDTO summoner = new SummonerDTO();
        assertEquals(Integer.valueOf(3), summoner.getLeagueShard());
        assertEquals(Integer.valueOf(0), summoner.getTracking());
        
        ParticipantDTO participant = new ParticipantDTO();
        assertEquals("", participant.getKda());
        assertEquals(Byte.valueOf((byte) 0), participant.getSubteam());
        assertEquals(Byte.valueOf((byte) 0), participant.getSubteamPlacement());
        assertEquals(Integer.valueOf(0), participant.getDamage());
        assertEquals(Integer.valueOf(0), participant.getDamageBuilding());
        assertEquals(Integer.valueOf(0), participant.getHealing());
        assertEquals(Short.valueOf((short) 0), participant.getCs());
        assertEquals("{}", participant.getPings());
    }
}
