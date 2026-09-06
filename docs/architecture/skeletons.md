# Skeleton finale — dev wipe, solo nuovo shape

> Flusso invariato fino a project(). Poi coalesce in 1 doc per scope. Solo raw su Mongo, derived runtime. Leaf condiviso.

## Shared
```java
// lol/model/statistics/shared/LeafStats.java
public class LeafStats {
    long games; long wins;
    long kills; long deaths; long assists;
    long losses() { return games - wins; }
    double winrate() { return games==0?0:(double)wins/games; }
    double kda() { return deaths==0? kills+assists : (double)(kills+assists)/deaths; }
    void merge(LeafStats o){ games+=o.games; wins+=o.wins; kills+=o.kills; deaths+=o.deaths; assists+=o.assists; }
}

// ChampionLeafStats extends LeafStats
class ChampionLeafStats extends LeafStats {
    double csm; long csmGames; // csPerMinute = csm/csmGames
    double gpm; long gpmGames;
    TrendStats trend; // {games,wins}
    Map<Integer, MatchupStats> matchups; // opp -> {games,wins, goldDiff,goldDiffGames, csDiff,csDiffGames, soloKills,kills, kp,kpGames, metricGames}
    Map<String, Map<Integer, WinLossStats>> synergies; // allyLane -> ally -> {games,wins}
    Map<String, WinLossStats> powerCurve; // bucket "0-15" -> {games,wins}
    void merge(ChampionLeafStats o){ super.merge(o); csm+=o.csm; csmGames+=o.csmGames; gpm+=o.gpm; gpmGames+=o.gpmGames; /* merge maps */ }
}

// ProfileLeafStats extends LeafStats
class ProfileLeafStats extends LeafStats {
    long blueGames, blueWins, redGames, redWins;
    long damage, damageBuilding; Long damageTaken; long healing, vision, ward, wardKilled;
    long cs, gold, lpGain; Long championLevelTotal;
    long doubles,triples,quadruples,pentas; long q,w,e,r,d,f;
    long arenaFirst, arenaSecond, arenaThird, arenaPlacementSum;
    long playtime, lastPlayedAt; double killParticipationSum, deathShareSum;
}
record WinLossStats(long games, long wins){}
record MatchupStats(long games,long wins, long goldDiff,long goldDiffGames, long csDiff,long csDiffGames, long soloKills,long kills, double kp,long kpGames, long metricGames){}
record TrendStats(long games,long wins){}
```

## profile_statistics — puuid+filterKey
```
{ puuid, filterKey, timeStart, timeEnd, lastUpdate,
  champions: { "157": { "RANKED_SOLO": { "MID": ProfileLeafStats } } },
  pings, spellOne, spellTwo }
```

## champion_statistics — 1 doc per scope (NUOVO)
```
{
  _id: "TEAM_BUILDER_RANKED_SOLO|GREATER_OR_EQUAL|EMERALD|16.15|EUW1",
  scope: { queue, rank, rankBehavior, patch, region, timeStart, timeEnd },
  games: 36765, banGames: 36000, previousPatch: "16.14",
  ready: true, updatedAt: ...,
  champions: {
    "64": { bans: 1276, lanes: { "JUNGLE": ChampionLeafStats, "TOP": ChampionLeafStats } },
    "157": { bans: ..., lanes: { "MID": ChampionLeafStats } }
  }
}
```
- No doc per lane. Lane = key dentro `lanes`.
- `lane=null` non persistito → `overall = new ChampionLeafStats(); for(lane: lanes.values()) overall.merge(lane)` (merge transient per API/Discord/tier overall).
- Tier list: `for(lane: playables) source = doc.champions[champ].lanes[lane]` → picks=games, wins, winrate, banrate=bans/banGames, matchup da `leaf.matchups`. Nessuna overview. Eligibility legge più lane dallo stesso doc.
- `Stats.java` eliminato, derived solo metodi.

## Flusso nuovo
```
MATCHES → Provider → RawMatrix → bucket → project(TOP/JGL/MID...) INVARIATO → coalesce per scope → 1 ChampionStatsDocument → Mongo replaceOne(upsert)
```
Dev: svuota `champion_stats`, `profile_statistics`, Redis. Primo refresh ricostruisce tutto nuovo.
