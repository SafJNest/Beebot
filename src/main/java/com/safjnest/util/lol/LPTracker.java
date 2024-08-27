package com.safjnest.util.lol;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public class LPTracker {

    private static R4J api = LeagueHandler.getRiotApi();

	public LPTracker() {

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		long period = TimeConstant.MINUTE * 20;

		Runnable task = new Runnable() {
			@Override
			public void run() {
                try {trackSummoners();}
                catch (Exception e) {e.printStackTrace();}
				
			}
		};

		scheduler.scheduleAtFixedRate(task, TimeConstant.MINUTE, period, TimeUnit.MILLISECONDS);
	}

	private void trackSummoners() {
		QueryResult result = DatabaseHandler.getRegistredLolAccount();

        for (ResultRow account : result) {
            Summoner summoner = LeagueHandler.getSummonerByAccountId(account.get("account_id"), LeagueShard.values()[Integer.valueOf(account.get("league_shard"))]);
            if (summoner != null) analyzeMatchHistory(summoner, account);
        }
	}

    private void analyzeMatchHistory(Summoner summoner, ResultRow dataGame) {
        BotLogger.trace("[LPTracker] Analyzing match history for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");

        LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner);
        
        try { Thread.sleep(500); } 
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = summoner.getLeagueGames().withCount(20).withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
        if (matchIds.isEmpty()) return;

        String matchId = matchIds.get(0);
        LOLMatch match = api.getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);

        if (!LeagueHandler.isCurrentSplit(match.getGameStartTimestamp())) return;
        
        boolean win = false;
        int champion = 0;
        for (MatchParticipant partecipant : match.getParticipants()) {
            if (partecipant.getSummonerId().equals(summoner.getSummonerId())) {
                win = partecipant.didWin();
                champion = partecipant.getChampionId();
            }
        }

        if (match.getGameId() == dataGame.getAsLong("game_id")) return;

        LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY, summoner);
        
        try { Thread.sleep(500); } 
        catch (InterruptedException e) { }

        LeagueEntry league = summoner.getLeagueEntry().stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);
        
        TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;

        int rank = division.ordinal();
        int lp = league != null ? league.getLeaguePoints() : 0;
        int gain = 0;

        if (dataGame.get("rank") == null) gain = 0;
        else if ((division != TierDivisionType.CHALLENGER_I && division != TierDivisionType.GRANDMASTER_I && division != TierDivisionType.MASTER_I) && rank != dataGame.getAsInt("rank")) {
            gain = 100 - (Math.abs(lp - dataGame.getAsInt("lp")));
            gain = rank < dataGame.getAsInt("rank") ? gain : -gain;
        }
        else gain = lp - dataGame.getAsInt("lp");
        
        BotLogger.info("[LPTracker] Push match history for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");
        DatabaseHandler.setSummonerData(summoner.getAccountId(), match.getGameId(), summoner.getPlatform(), win, rank, lp, gain, champion, match.getGameCreation(), match.getGameEndTimestamp(), match.getGameVersion());

    }

}
