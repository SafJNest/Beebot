package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.match.LiveGame;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;

public class LiveGameTest {

    @Test
    public void shouldReturnAnEmptyPayloadWhenTheSummonerIsNotInGame() {
        LiveGame game = LiveGame.empty();

        assertTrue(game.notInGame());
        assertNull(game.gameId());
        assertTrue(game.bans().isEmpty());
        assertTrue(game.participants().isEmpty());
    }

    @Test
    public void shouldMapSpectatorParticipantsAndPersistedProfileOverview() {
        SpectatorGameInfo source = JsonCodec.fromJson("""
            {
              "gameId": 42,
              "gameLength": 120,
              "gameMode": "CLASSIC",
              "gameQueueConfigId": "RANKED_SOLO_5X5",
              "gameStartTime": 1714521600000,
              "gameType": "MATCHED_GAME",
              "mapId": "SUMMONERS_RIFT",
              "platformId": "EUW1",
              "bannedChampions": [{"championId": 157, "teamId": 100}],
              "participants": [{
                "championId": 157,
                "profileIconId": 29,
                "spell1Id": "FLASH",
                "spell2Id": "IGNITE",
                "summonerId": "summoner-1",
                "puuid": "puuid-1",
                "riotId": "Player#EUW",
                "teamId": "BLUE",
                "perks": {"perkIds": [8005, 9111, 9104, 8014, 8345, 8347, 5008, 5008, 5010], "perkStyle": 8000, "perkSubStyle": 8100}
              }, {
                "championId": 110,
                "riotId": "spectator-name-is-ignored",
                "teamId": "RED"
              }]
            }
            """, SpectatorGameInfo.class);
        Stats<Integer> playedChampionStats = new Stats<>(157);
        playedChampionStats.games = 12;
        Stats<Integer> secondChampionStats = new Stats<>(238);
        secondChampionStats.games = 10;
        Stats<Integer> thirdChampionStats = new Stats<>(64);
        thirdChampionStats.games = 8;
        LiveGame.ProfileOverview overview = new LiveGame.ProfileOverview(
            new Summoner("puuid-1", "Player#EUW", no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1, 100, 29),
            Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(null, 50, 10, 5)),
            List.of(new Mastery(157, 7, 200_000)),
            List.of(playedChampionStats, secondChampionStats, thirdChampionStats)
        );

        LiveGame game = LiveGame.fromR4J(source, Map.of("puuid-1", overview));

        assertFalse(game.notInGame());
        assertEquals(Long.valueOf(42), game.gameId());
        assertEquals(List.of(157), game.bans().get(TeamType.BLUE));
        assertEquals(2, game.participants().size());
        assertEquals(Integer.valueOf(4), game.participants().getFirst().summonerSpell1());
        assertEquals(Integer.valueOf(14), game.participants().getFirst().summonerSpell2());
        assertEquals(8000, game.participants().getFirst().runes().primaryTree());
        assertEquals(8005, game.participants().getFirst().runes().keystone());
        assertEquals(List.of(9111, 9104, 8014), game.participants().getFirst().runes().primaryRunes());
        assertEquals(List.of(8345, 8347), game.participants().getFirst().runes().secondaryRunes());
        assertEquals(List.of(5008, 5008, 5010), game.participants().getFirst().runes().statShards());
        assertEquals(157, game.participants().getFirst().profileOverview().masteries().getFirst().championId());
        assertTrue(game.participants().getFirst().profileOverview().championStats().containsKey(157));
        assertEquals(3, game.participants().getFirst().profileOverview().championStats().size());
        assertNull(game.participants().get(1).puuid());
        assertEquals(110, game.participants().get(1).championId());
        assertNotNull(game.participants().get(1).riotId());
        assertFalse("spectator-name-is-ignored".equals(game.participants().get(1).riotId()));
        assertNull(game.participants().get(1).icon());
        assertEquals(TeamType.RED, game.participants().get(1).team());
        assertNull(game.participants().get(1).summonerSpell1());
        assertNull(game.participants().get(1).summonerSpell2());
        assertNull(game.participants().get(1).runes());
        assertNull(game.participants().get(1).profileOverview());
    }
}
