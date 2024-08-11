package com.safjnest.util.LOL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.calling.DataCall;
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

    private static R4J api = RiotHandler.getRiotApi();

	public LPTracker() {

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		long period = TimeConstant.MINUTE * 15;

		Runnable task = new Runnable() {
			@Override
			public void run() {
                try {trackSummoners();}
                catch (Exception e) {e.printStackTrace();}
				
			}
		};

		scheduler.scheduleAtFixedRate(task, 0, period, TimeUnit.MILLISECONDS);
	}

	private void trackSummoners() {
        BotLogger.info("[LPTracker] Starting daily task");
		QueryResult result = DatabaseHandler.getRegistredLolAccount();

        for (ResultRow account : result) {
            Summoner summoner = RiotHandler.getSummonerByAccountId(account.get("account_id"), LeagueShard.values()[Integer.valueOf(account.get("league_shard"))]);
            if (summoner != null) analyzeMatchHistory(summoner, account);
        }
	}

    private void analyzeMatchHistory(Summoner summoner, ResultRow dataGame) {
        BotLogger.trace("[LPTracker] Analyzing match history for " + RiotHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");
        long now = System.currentTimeMillis();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("platform", summoner.getPlatform().toRegionShard());
        data.put("puuid", summoner.getPUUID());
        data.put("queue", GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        data.put("type", "null");
        data.put("start", "null");
        data.put("count", 20);
        data.put("startTime", "null");
        data.put("endTime", "null");
        DataCall.getCacheProvider().clear(URLEndpoint.V5_MATCHLIST, data);
        
        try { Thread.sleep(500); } 
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = summoner.getLeagueGames().withCount(20).withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
        if (matchIds.isEmpty()) return;

        String matchId = matchIds.get(0);
        LOLMatch match = api.getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);

        if (now - match.getGameStartTimestamp() > TimeConstant.MONTH * 3) return;
        
        boolean win = false;
        int champion = 0;
        for (MatchParticipant partecipant : match.getParticipants()) {
            if (partecipant.getSummonerId().equals(summoner.getSummonerId())) {
                win = partecipant.didWin();
                champion = partecipant.getChampionId();
            }
        }

        if (match.getGameId() == dataGame.getAsLong("game_id")) return;


        data = new LinkedHashMap<>();
        data.put("platform", summoner.getPlatform());
        data.put("id", summoner.getSummonerId());
        DataCall.getCacheProvider().clear(URLEndpoint.V4_LEAGUE_ENTRY, data);
        
        try { Thread.sleep(500); } 
        catch (InterruptedException e) { }

        LeagueEntry league = summoner.getLeagueEntry().stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);
        
        TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;

        int rank = division.ordinal();
        int lp = league != null ? league.getLeaguePoints() : 0;
        int gain = 0;

        if (dataGame.get("rank") == null) gain = 0;
        else if ((division != TierDivisionType.CHALLENGER_I || division != TierDivisionType.GRANDMASTER_I || division != TierDivisionType.MASTER_I) && rank != dataGame.getAsInt("rank")) {
            gain = 100 - (Math.abs(lp - dataGame.getAsInt("lp")));
            gain = rank < dataGame.getAsInt("rank") ? gain : -gain;
        }
        else gain = lp - dataGame.getAsInt("lp");
        
        //String account_id, long game_id, boolean win, int rank, int lp, int gain, long time, String version
        DatabaseHandler.setSummonerData(summoner.getAccountId(), match.getGameId(), summoner.getPlatform(), win, rank, lp, gain, champion, match.getGameCreation(), match.getGameEndTimestamp(), match.getGameVersion());

    }

}
