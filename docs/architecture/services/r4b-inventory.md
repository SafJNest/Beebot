# Inventario operativo R4B

Baseline: branch `beebot-servicies`, HEAD `8a88645e`, working tree inizialmente pulito.
Decisione mirata: [ADR-0018](../adr/0018-r4b-shared-contracts.md).

## SDK realmente utilizzato

`pom.xml` dichiara `com.github.stelar7:R4J:2.8.1`. Il confronto usa il JAR
locale della stessa versione e i [sorgenti ufficiali del tag 2.8.1](https://github.com/stelar7/R4J/tree/2.8.1).
Le famiglie enum preservano l’insieme completo e l’ordine della versione
dichiarata, inclusi valori storici, sconosciuti e ID condivisi.

La tabella è il catalogo dei riferimenti nominali baseline (inclusi test e
nomi completamente qualificati), integrato dalle esplorazioni CodeGraph e dalle
prove di compilazione/chiusura descritte nel documento di validazione. Non è
presentata come prova autonoma di indipendenza. Firme, generics, superclassi,
componenti annidati e inizializzazioni sono verificati dalla compilazione e
dall’analisi delle dipendenze dei classfile.

| Tipo SDK baseline | Consumatori baseline | Destinazione/conversione |
|---|---|---|
| `no.stelar7.api.r4j.basic.APICredentials` | `src/main/java/com/safjnest/lol/LeagueHandler.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.basic.cache.impl.EmptyCacheProvider` | `src/main/java/com/safjnest/lol/LeagueHandler.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.basic.calling.DataCall` | `src/main/java/com/safjnest/lol/LeagueHandler.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.basic.constants.api.URLEndpoint` | `src/main/java/com/safjnest/lol/service/MatchService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard` | `src/main/java/com/safjnest/commands/lol/Champion.java`<br>`src/main/java/com/safjnest/commands/lol/Champions.java`<br>`src/main/java/com/safjnest/commands/lol/Region.java`<br>`src/main/java/com/safjnest/commands/owner/Test.java`<br>`src/main/java/com/safjnest/core/events/EventAutoCompleteInteractionHandler.java`<br>`src/main/java/com/safjnest/core/events/EventModalInteractionHandler.java`<br>`src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsData.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsProvider.java`<br>`src/main/java/com/safjnest/lol/message/LeagueEventHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`<br>`src/main/java/com/safjnest/lol/model/Filter.java`<br>`src/main/java/com/safjnest/lol/model/competitive/CompetitiveEntry.java`<br>`src/main/java/com/safjnest/lol/model/match/LiveGame.java`<br>`src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/model/record/ProfileRecord.java`<br>`src/main/java/com/safjnest/lol/model/statistics/shared/ChampionStatsScope.java`<br>`src/main/java/com/safjnest/lol/model/summoner/Summoner.java`<br>`src/main/java/com/safjnest/lol/scheduler/ComputeScheduler.java`<br>`src/main/java/com/safjnest/lol/scheduler/SyncScheduler.java`<br>`src/main/java/com/safjnest/lol/service/ChampionAnalyzer.java`<br>`src/main/java/com/safjnest/lol/service/ChampionService.java`<br>`src/main/java/com/safjnest/lol/service/CompetitiveService.java`<br>`src/main/java/com/safjnest/lol/service/LeaderboardService.java`<br>`src/main/java/com/safjnest/lol/service/MasteryService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/service/ProfileRecordService.java`<br>`src/main/java/com/safjnest/lol/service/ProfileService.java`<br>`src/main/java/com/safjnest/lol/service/RankService.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueShardUtils.java`<br>`src/main/java/com/safjnest/lol/utils/MatchUtils.java`<br>`src/main/java/com/safjnest/model/UserData.java`<br>`src/main/java/com/safjnest/model/guild/ChannelData.java`<br>`src/main/java/com/safjnest/model/guild/GuildData.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/r4j/scheduler/RiotScheduler.java`<br>`src/main/java/com/safjnest/spring/controller/LolApiParameters.java`<br>`src/main/java/com/safjnest/spring/controller/LolController.java`<br>`src/main/java/com/safjnest/sql/QueryRecord.java`<br>`src/main/java/com/safjnest/sql/database/BotDB.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/model/FilterTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileStatisticsTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/CompetitiveServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileRecordAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/SummonerServiceTest.java`<br>`src/test/java/com/safjnest/mongo/MongoDBTest.java`<br>`src/test/java/com/safjnest/mongo/SummonerWarmingTest.java`<br>`src/test/java/com/safjnest/nosql/AbstractEntityTest.java`<br>`src/test/java/com/safjnest/nosql/MongoLeaderboardTest.java`<br>`src/test/java/com/safjnest/spring/controller/ChampionControllerTest.java`<br>`src/test/java/com/safjnest/spring/controller/LolControllerTest.java` | Valore indipendente `com.R4B.LOL.LeagueShard`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1` | `src/test/java/com/safjnest/lol/model/LiveGameTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileServiceTest.java`<br>`src/test/java/com/safjnest/mongo/MongoDBTest.java`<br>`src/test/java/com/safjnest/nosql/AbstractEntityTest.java`<br>`src/test/java/com/safjnest/spring/config/LolApiConfigTest.java` | Valore indipendente `com.R4B.LOL.EUW1`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.api.regions.RegionShard` | `src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueShardUtils.java`<br>`src/main/java/com/safjnest/model/guild/GuildData.java` | Valore indipendente `com.R4B.LOL.RegionShard`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.KillType` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Valore indipendente `com.R4B.LOL.KillType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameModeType` | `src/main/java/com/safjnest/lol/model/match/LiveGame.java` | Valore indipendente `com.R4B.LOL.GameModeType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` | `src/main/java/com/safjnest/commands/lol/Champion.java`<br>`src/main/java/com/safjnest/commands/lol/Champions.java`<br>`src/main/java/com/safjnest/commands/owner/ChampionStats.java`<br>`src/main/java/com/safjnest/commands/owner/Test.java`<br>`src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueEventHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`<br>`src/main/java/com/safjnest/lol/model/Filter.java`<br>`src/main/java/com/safjnest/lol/model/competitive/CompetitiveEntry.java`<br>`src/main/java/com/safjnest/lol/model/match/LiveGame.java`<br>`src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/model/match/MatchResult.java`<br>`src/main/java/com/safjnest/lol/model/match/RankHistoryMatch.java`<br>`src/main/java/com/safjnest/lol/model/match/RankHistoryQuery.java`<br>`src/main/java/com/safjnest/lol/model/statistics/CanonicalQueue.java`<br>`src/main/java/com/safjnest/lol/model/statistics/ProfileActivity.java`<br>`src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`<br>`src/main/java/com/safjnest/lol/model/statistics/shared/ChampionStatsScope.java`<br>`src/main/java/com/safjnest/lol/model/summoner/Summoner.java`<br>`src/main/java/com/safjnest/lol/model/summoner/SummonerView.java`<br>`src/main/java/com/safjnest/lol/scheduler/ComputeScheduler.java`<br>`src/main/java/com/safjnest/lol/service/CacheInvalidationService.java`<br>`src/main/java/com/safjnest/lol/service/ChampionService.java`<br>`src/main/java/com/safjnest/lol/service/CompetitiveService.java`<br>`src/main/java/com/safjnest/lol/service/LeaderboardService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/service/ProfileService.java`<br>`src/main/java/com/safjnest/lol/service/RankService.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/tracker/TrackerScheduler.java`<br>`src/main/java/com/safjnest/lol/utils/GameQueueTypeUtils.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java`<br>`src/main/java/com/safjnest/lol/utils/RankProgressUtils.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/nosql/MongoMigration.java`<br>`src/main/java/com/safjnest/spring/controller/ChampionController.java`<br>`src/main/java/com/safjnest/spring/controller/LolApiParameters.java`<br>`src/main/java/com/safjnest/spring/controller/LolController.java`<br>`src/main/java/com/safjnest/sql/QueryRecord.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/message/LeagueMessageOpggTitleTest.java`<br>`src/test/java/com/safjnest/lol/model/FilterTest.java`<br>`src/test/java/com/safjnest/lol/model/LiveGameTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileActivityTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileMatchupsTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileStatisticsTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionServiceMatrixTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionTierAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/CompetitiveServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileServiceTest.java`<br>`src/test/java/com/safjnest/lol/tracker/TrackerTest.java`<br>`src/test/java/com/safjnest/mongo/MongoDBTest.java`<br>`src/test/java/com/safjnest/mongo/MongoMigrationTest.java`<br>`src/test/java/com/safjnest/mongo/SummonerWarmingTest.java`<br>`src/test/java/com/safjnest/nosql/AbstractEntityTest.java`<br>`src/test/java/com/safjnest/nosql/MongoChampionBuildRecordTest.java`<br>`src/test/java/com/safjnest/nosql/MongoChampionStatisticsDocumentTest.java`<br>`src/test/java/com/safjnest/nosql/MongoLeaderboardTest.java`<br>`src/test/java/com/safjnest/spring/config/LolApiConfigTest.java`<br>`src/test/java/com/safjnest/spring/controller/ChampionControllerTest.java`<br>`src/test/java/com/safjnest/spring/controller/LolControllerTest.java` | Valore indipendente `com.R4B.LOL.GameQueueType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType.RANKED_FLEX_SR` | `src/test/java/com/safjnest/lol/model/ActivityFilterTest.java` | Valore indipendente `com.R4B.LOL.RANKED_FLEX_SR`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameType` | `src/main/java/com/safjnest/lol/model/match/LiveGame.java` | Valore indipendente `com.R4B.LOL.GameType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` | `src/main/java/com/safjnest/commands/lol/Champion.java`<br>`src/main/java/com/safjnest/commands/lol/Champions.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsData.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsProvider.java`<br>`src/main/java/com/safjnest/lol/message/LeagueEventHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`<br>`src/main/java/com/safjnest/lol/model/ChampionIndexable.java`<br>`src/main/java/com/safjnest/lol/model/ChampionStatistics.java`<br>`src/main/java/com/safjnest/lol/model/ChampionTierList.java`<br>`src/main/java/com/safjnest/lol/model/Filter.java`<br>`src/main/java/com/safjnest/lol/model/competitive/CompetitiveEntry.java`<br>`src/main/java/com/safjnest/lol/model/match/MatchResult.java`<br>`src/main/java/com/safjnest/lol/model/match/Participant.java`<br>`src/main/java/com/safjnest/lol/model/match/RankHistoryMatch.java`<br>`src/main/java/com/safjnest/lol/model/statistics/ProfileMatchups.java`<br>`src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`<br>`src/main/java/com/safjnest/lol/service/ChampionAnalyzer.java`<br>`src/main/java/com/safjnest/lol/service/ChampionService.java`<br>`src/main/java/com/safjnest/lol/service/CompetitiveService.java`<br>`src/main/java/com/safjnest/lol/service/LeaderboardService.java`<br>`src/main/java/com/safjnest/lol/service/MatchupRankingAnalyzer.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/LaneTypeUtils.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java`<br>`src/main/java/com/safjnest/lol/utils/OPTUtils.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/spring/controller/ChampionController.java`<br>`src/main/java/com/safjnest/spring/controller/LolApiParameters.java`<br>`src/main/java/com/safjnest/sql/QueryRecord.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/model/ChampionLeafStatsTest.java`<br>`src/test/java/com/safjnest/lol/model/ChampionStatisticsTest.java`<br>`src/test/java/com/safjnest/lol/model/FilterTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileMatchupsTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileStatisticsTest.java`<br>`src/test/java/com/safjnest/lol/model/RankHistoryMatchTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionTierAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/CompetitiveServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/MatchupRankingAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileServiceTest.java`<br>`src/test/java/com/safjnest/lol/utils/LaneTypeUtilsTest.java`<br>`src/test/java/com/safjnest/nosql/MongoChampionStatisticsDocumentTest.java`<br>`src/test/java/com/safjnest/nosql/MongoLeaderboardTest.java`<br>`src/test/java/com/safjnest/spring/config/LolApiConfigTest.java`<br>`src/test/java/com/safjnest/spring/controller/ChampionControllerTest.java`<br>`src/test/java/com/safjnest/spring/controller/LolControllerTest.java` | Valore indipendente `com.R4B.LOL.LaneType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType.NONE` | `src/test/java/com/safjnest/lol/service/ChampionServiceMatrixTest.java` | Valore indipendente `com.R4B.LOL.NONE`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType.TOP` | `src/test/java/com/safjnest/lol/model/ActivityFilterTest.java` | Valore indipendente `com.R4B.LOL.TOP`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType.UTILITY` | `src/test/java/com/safjnest/lol/model/FilterTest.java` | Valore indipendente `com.R4B.LOL.UTILITY`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class` | `src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.class`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MapType` | `src/main/java/com/safjnest/lol/model/match/LiveGame.java` | Valore indipendente `com.R4B.LOL.MapType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType` | `src/main/java/com/safjnest/lol/service/CacheInvalidationService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java` | Valore indipendente `com.R4B.LOL.MatchlistMatchType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.SummonerSpellType` | `src/main/java/com/safjnest/lol/model/match/LiveGame.java` | Valore indipendente `com.R4B.LOL.SummonerSpellType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` | `src/main/java/com/safjnest/lol/champion/ChampionStatsData.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsProvider.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/model/match/LiveGame.java`<br>`src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/model/match/Participant.java`<br>`src/main/java/com/safjnest/lol/model/match/RankHistoryMatch.java`<br>`src/main/java/com/safjnest/lol/model/record/ProfileRecord.java`<br>`src/main/java/com/safjnest/lol/model/statistics/shared/ProfileLeafStats.java`<br>`src/main/java/com/safjnest/lol/service/ChampionAnalyzer.java`<br>`src/main/java/com/safjnest/lol/service/ProfileRecordAnalyzer.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/sql/QueryRecord.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/model/LiveGameTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileMatchupsTest.java`<br>`src/test/java/com/safjnest/lol/model/ProfileStatisticsTest.java`<br>`src/test/java/com/safjnest/lol/model/RankHistoryMatchTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileRecordAnalyzerTest.java`<br>`src/test/java/com/safjnest/mongo/MongoDBTest.java` | Valore indipendente `com.R4B.LOL.TeamType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE` | `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.BLUE`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED` | `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.RED`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType.SUBTEAM` | `src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.SUBTEAM`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType.class` | `src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.class`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType.valueOf` | `src/main/java/com/safjnest/nosql/MongoDB.java` | Valore indipendente `com.R4B.LOL.valueOf`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType` | `src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/model/match/RankProgress.java`<br>`src/main/java/com/safjnest/lol/model/summoner/Rank.java`<br>`src/main/java/com/safjnest/lol/service/LeaderboardService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/service/RankService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/RankProgressUtils.java`<br>`src/main/java/com/safjnest/lol/utils/TierDivisionUtils.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/nosql/MongoMigration.java`<br>`src/main/java/com/safjnest/sql/QueryRecord.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/message/LeagueMessageOpggTitleTest.java`<br>`src/test/java/com/safjnest/lol/service/CompetitiveServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/LeaderboardServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileRecordAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ProfileServiceTest.java`<br>`src/test/java/com/safjnest/lol/service/SummonerServiceTest.java`<br>`src/test/java/com/safjnest/lol/tracker/TrackerTest.java`<br>`src/test/java/com/safjnest/mongo/MongoDBTest.java`<br>`src/test/java/com/safjnest/mongo/MongoMigrationTest.java`<br>`src/test/java/com/safjnest/mongo/SummonerWarmingTest.java`<br>`src/test/java/com/safjnest/nosql/AbstractEntityTest.java`<br>`src/test/java/com/safjnest/nosql/MongoLeaderboardTest.java`<br>`src/test/java/com/safjnest/spring/config/LolApiConfigTest.java` | Valore indipendente `com.R4B.LOL.TierDivisionType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType.UNRANKED` | `src/main/java/com/safjnest/lol/LeagueHandler.java` | Valore indipendente `com.R4B.LOL.UNRANKED`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TierType` | `src/main/java/com/safjnest/commands/lol/Champion.java`<br>`src/main/java/com/safjnest/commands/lol/Champions.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsData.java`<br>`src/main/java/com/safjnest/lol/champion/ChampionStatsProvider.java`<br>`src/main/java/com/safjnest/lol/message/LeagueEventHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`<br>`src/main/java/com/safjnest/lol/model/Filter.java`<br>`src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`<br>`src/main/java/com/safjnest/lol/model/statistics/shared/ChampionStatsScope.java`<br>`src/main/java/com/safjnest/lol/service/ChampionAnalyzer.java`<br>`src/main/java/com/safjnest/lol/service/ChampionService.java`<br>`src/main/java/com/safjnest/lol/service/LeaderboardService.java`<br>`src/main/java/com/safjnest/lol/utils/TierDivisionUtils.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java`<br>`src/main/java/com/safjnest/spring/controller/LolApiParameters.java`<br>`src/main/java/com/safjnest/sql/database/LeagueDB.java`<br>`src/test/java/com/safjnest/lol/model/FilterTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionAnalyzerTest.java`<br>`src/test/java/com/safjnest/lol/service/ChampionServiceMatrixTest.java`<br>`src/test/java/com/safjnest/nosql/MongoLeaderboardTest.java`<br>`src/test/java/com/safjnest/spring/controller/ChampionControllerTest.java` | Valore indipendente `com.R4B.LOL.TierType`; conversione SDK nel lato `r4j` |
| `no.stelar7.api.r4j.impl.R4J` | `src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/service/MasteryService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/service/RankService.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder` | `src/main/java/com/safjnest/lol/service/MatchService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery` | `src/main/java/com/safjnest/lol/service/MasteryService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.league.LeagueEntry` | `src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/service/RankService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch` | `src/main/java/com/safjnest/lol/model/match/Match.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/MatchUtils.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline` | `src/main/java/com/safjnest/commands/owner/Test.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant` | `src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection` | `src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle` | `src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrame` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineParticipantFrame` | `src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.shared.BannedChampion` | `src/main/java/com/safjnest/lol/model/match/LiveGame.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo` | `src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/model/match/LiveGame.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java`<br>`src/test/java/com/safjnest/lol/model/LiveGameTest.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant` | `src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/model/match/LiveGame.java`<br>`src/main/java/com/safjnest/nosql/MongoDB.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion` | `src/main/java/com/safjnest/commands/lol/Champion.java`<br>`src/main/java/com/safjnest/commands/lol/Champions.java`<br>`src/main/java/com/safjnest/commands/lol/UltimateBravery.java`<br>`src/main/java/com/safjnest/core/events/EventModalInteractionHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueEventHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`<br>`src/main/java/com/safjnest/lol/service/ChampionService.java`<br>`src/main/java/com/safjnest/lol/service/ChampionTierAnalyzer.java`<br>`src/main/java/com/safjnest/lol/service/StaticDataService.java`<br>`src/main/java/com/safjnest/lol/utils/ChampionUtils.java`<br>`src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java`<br>`src/main/java/com/safjnest/spring/controller/LolApiParameters.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.staticdata.item.Item` | `src/main/java/com/safjnest/commands/lol/Item.java`<br>`src/main/java/com/safjnest/core/events/EventAutoCompleteInteractionHandler.java`<br>`src/main/java/com/safjnest/lol/champion/BuildSignature.java`<br>`src/main/java/com/safjnest/lol/service/StaticDataService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java`<br>`src/main/java/com/safjnest/lol/utils/BuildUtils.java`<br>`src/main/java/com/safjnest/lol/utils/ItemUtils.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.staticdata.summonerspell.StaticSummonerSpell` | `src/main/java/com/safjnest/lol/service/StaticDataService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.summoner.Summoner` | `src/main/java/com/safjnest/commands/owner/Test.java`<br>`src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/message/LeagueMessage.java`<br>`src/main/java/com/safjnest/lol/service/CacheInvalidationService.java`<br>`src/main/java/com/safjnest/lol/service/MatchService.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java`<br>`src/main/java/com/safjnest/lol/tracker/Tracker.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.lol.summoner.Summoner.class` | `src/main/java/com/safjnest/lol/service/SummonerService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |
| `no.stelar7.api.r4j.pojo.shared.RiotAccount` | `src/main/java/com/safjnest/lol/LeagueHandler.java`<br>`src/main/java/com/safjnest/lol/service/SummonerService.java` | Solo integrazione `r4j`; consumatori tramite dati acquisizione/canonici indipendenti |

## Catalogo endpoint e chiusure pubbliche

Il catalogo completo delle famiglie di risposte e dei componenti serializzati
è in [shared-contracts.md](shared-contracts.md#catalogo-dei-contratti-finali).
Il gate JSON parte dalle fixture reali prodotte dalla versione baseline;
include errori, metadata, status e componenti annidati oltre alle sole radici.

| Endpoint/famiglia | Radici/chiusura dati | Proprietario produttore |
|---|---|---|
| Profile PUUID/Riot ID, search | SummonerView → Summoner, Rank, SummonerOverview → ProfileStatistics, Mastery, Champion, ProfileLeafStats, LeafStats; ResponseMetadata/Filter | SummonerService/ProfileService |
| Matches/detail | MatchPage, MatchResult, Match → Participant, RankProgress, bans e valori match; metadata/filtri | MatchService/MongoDB; acquisizione r4j |
| Livegame | LiveGame → partecipanti, bans, SummonerView/overview, RuneSignature e valori spectator | SummonerService; SDK spectator r4j |
| Rank history | RankHistory, RankHistoryMatch, RankHistoryMetadata, RankHistoryView, RankHistoryQuery, RankProgress | MatchService |
| Activity | ProfileActivity e Coverage/Summary/Heatmap/finestre/righe/sessioni/insight | ProfileService/ProfileAnalyzer |
| Matchups | ProfileMatchups, ProfileMatchupLeaf, ProfileLeafStats, CanonicalQueue | ProfileService/ProfileAnalyzer/MatchupRankingAnalyzer |
| Profile/global records | ProfileRecordPage, RecordsOverview, RecordPage, ProfileRecord, RecordMetric, RecordOwner | ProfileRecordService/ProfileRecordAnalyzer |
| Champion page/build/statistics | ChampionView, ChampionStatistics, Build e Option/CoreBuildOption/RuneOption/SkillOrderOption/SkillTiming, RuneSignature | ChampionService/ChampionAnalyzer/ChampionBuildEngine |
| Champion tier list | ChampionTierList e statistics/champion/role/relation finali | ChampionTierAnalyzer |
| Leaderboard/distribution/top-regions | LeaderboardPage, SummonerLeaderboard → SummonerView, LeaderboardDistribution | LeaderboardService |
| Indexables | ProfileIndexable, ChampionIndexable (array root) | Servizi indexables/producer |
| Status | BotStatus, League/Jvm/System/Redis/MongoMetrics e componenti Mongo; status generici Hive riutilizzati | com.safjnest.status |
| Error/metadata | LolApiError, ResponseMetadata/Pagination, ApiResult; READY/PARTIAL 200, PENDING 202, NOT_FOUND 404 | LolApiResponses/LolApiExceptionHandler; dati R4B |
| AI training | Streaming di mappe Mongo, nessun secondo DTO di dataset | AiTrainingController |

## Dipendenze operative escluse

R4B non possiede dirty tracking/AbstractEntity, query/upsert Mongo, BSON, Redis,
tracker, logger o bootstrap. Conversioni SDK e fetch rimangono in `r4j`;
accumulatori, aggregazioni, composizione applicativa e lookup statici rimangono
nel produttore. Emoji/opzioni Discord, shard abilitate e formattazione rimangono
nel bot. Il mapper HTTP conserva le esclusioni della baseline e non importa
il profilo di storage come contratto pubblico.


## Proprietari canonici trasferiti

La tabella seguente descrive sorgenti unici, non alias. Le strutture private
`ChampionLeafStats`, `MatchupStats`, `TrendStats`, `WinLossStats`,
`ChampionNode`, `ChampionStatsScope`, `ChampionStatsDocument` e gli intermedi
build restano nel produttore, perché non sono componenti del payload finale.

| Proprietario precedente | Proprietario R4B |
|---|---|
| `com.safjnest.lol.champion.RuneSignature` | `com.R4B.AB.DTO.RuneSignature` |
| `com.safjnest.lol.model.ActivityFilter` | `com.R4B.AB.DTO.ActivityFilter` |
| `com.safjnest.lol.model.ApiResult` | `com.R4B.AB.DTO.ApiResult` |
| `com.safjnest.lol.model.Build` | `com.R4B.AB.DTO.Build` |
| `com.safjnest.lol.model.ChampionIndexable` | `com.R4B.AB.DTO.ChampionIndexable` |
| `com.safjnest.lol.model.ChampionStatistics` | `com.R4B.AB.DTO.ChampionStatistics` |
| `com.safjnest.lol.model.ChampionTierList` | `com.R4B.AB.DTO.ChampionTierList` |
| `com.safjnest.lol.model.ChampionView` | `com.R4B.AB.DTO.ChampionView` |
| `com.safjnest.lol.model.Filter` | `com.R4B.AB.DTO.Filter` |
| `com.safjnest.lol.model.ProfileIndexable` | `com.R4B.AB.DTO.ProfileIndexable` |
| `com.safjnest.lol.model.ResponseMetadata` | `com.R4B.AB.DTO.ResponseMetadata` |
| `com.safjnest.lol.model.leaderboard.LeaderboardDistribution` | `com.R4B.AB.DTO.leaderboard.LeaderboardDistribution` |
| `com.safjnest.lol.model.leaderboard.LeaderboardPage` | `com.R4B.AB.DTO.leaderboard.LeaderboardPage` |
| `com.safjnest.lol.model.match.LiveGame` | `com.R4B.AB.DTO.match.LiveGame` |
| `com.safjnest.lol.model.match.Match` | `com.R4B.AB.DTO.match.Match` |
| `com.safjnest.lol.model.match.MatchOrder` | `com.R4B.LOL.MatchOrder` |
| `com.safjnest.lol.model.match.MatchPage` | `com.R4B.AB.DTO.match.MatchPage` |
| `com.safjnest.lol.model.match.MatchResult` | `com.R4B.AB.DTO.match.MatchResult` |
| `com.safjnest.lol.model.match.Participant` | `com.R4B.AB.DTO.match.Participant` |
| `com.safjnest.lol.model.match.RankHistory` | `com.R4B.AB.DTO.match.RankHistory` |
| `com.safjnest.lol.model.match.RankHistoryMatch` | `com.R4B.AB.DTO.match.RankHistoryMatch` |
| `com.safjnest.lol.model.match.RankHistoryMetadata` | `com.R4B.AB.DTO.match.RankHistoryMetadata` |
| `com.safjnest.lol.model.match.RankHistoryQuery` | `com.R4B.AB.DTO.match.RankHistoryQuery` |
| `com.safjnest.lol.model.match.RankHistoryView` | `com.R4B.LOL.RankHistoryView` |
| `com.safjnest.lol.model.match.RankProgress` | `com.R4B.AB.DTO.match.RankProgress` |
| `com.safjnest.lol.model.record.ProfileRecord` | `com.R4B.AB.DTO.record.ProfileRecord` |
| `com.safjnest.lol.model.record.ProfileRecordPage` | `com.R4B.AB.DTO.record.ProfileRecordPage` |
| `com.safjnest.lol.model.record.RecordMetric` | `com.R4B.LOL.RecordMetric` |
| `com.safjnest.lol.model.record.RecordOrder` | `com.R4B.LOL.RecordOrder` |
| `com.safjnest.lol.model.record.RecordOwner` | `com.R4B.LOL.RecordOwner` |
| `com.safjnest.lol.model.record.RecordPage` | `com.R4B.AB.DTO.record.RecordPage` |
| `com.safjnest.lol.model.record.RecordsOverview` | `com.R4B.AB.DTO.record.RecordsOverview` |
| `com.safjnest.lol.model.statistics.CanonicalQueue` | `com.R4B.LOL.CanonicalQueue` |
| `com.safjnest.lol.model.statistics.ProfileActivity` | `com.R4B.AB.DTO.statistics.ProfileActivity` |
| `com.safjnest.lol.model.statistics.ProfileMatchupLeaf` | `com.R4B.AB.DTO.statistics.ProfileMatchupLeaf` |
| `com.safjnest.lol.model.statistics.ProfileMatchups` | `com.R4B.AB.DTO.statistics.ProfileMatchups` |
| `com.safjnest.lol.model.statistics.ProfileStatistics` | `com.R4B.AB.DTO.statistics.ProfileStatistics` |
| `com.safjnest.lol.model.statistics.shared.LeafStats` | `com.R4B.AB.DTO.statistics.shared.LeafStats` |
| `com.safjnest.lol.model.statistics.shared.ProfileLeafStats` | `com.R4B.AB.DTO.statistics.shared.ProfileLeafStats` |
| `com.safjnest.lol.model.status.BotStatus` | `com.R4B.AB.DTO.status.BotStatus` |
| `com.safjnest.lol.model.status.JvmMetrics` | `com.R4B.AB.DTO.status.JvmMetrics` |
| `com.safjnest.lol.model.status.LeagueMetrics` | `com.R4B.AB.DTO.status.LeagueMetrics` |
| `com.safjnest.lol.model.status.MongoCollectionPerformance` | `com.R4B.AB.DTO.status.MongoCollectionPerformance` |
| `com.safjnest.lol.model.status.MongoHottestCollection` | `com.R4B.AB.DTO.status.MongoHottestCollection` |
| `com.safjnest.lol.model.status.MongoMemoryMetrics` | `com.R4B.AB.DTO.status.MongoMemoryMetrics` |
| `com.safjnest.lol.model.status.MongoMetrics` | `com.R4B.AB.DTO.status.MongoMetrics` |
| `com.safjnest.lol.model.status.MongoOperationRates` | `com.R4B.AB.DTO.status.MongoOperationRates` |
| `com.safjnest.lol.model.status.MongoOperationSample` | `com.R4B.AB.DTO.status.MongoOperationSample` |
| `com.safjnest.lol.model.status.MongoOperationsMetrics` | `com.R4B.AB.DTO.status.MongoOperationsMetrics` |
| `com.safjnest.lol.model.status.MongoPerformanceMetrics` | `com.R4B.AB.DTO.status.MongoPerformanceMetrics` |
| `com.safjnest.lol.model.status.MongoSlowOperation` | `com.R4B.AB.DTO.status.MongoSlowOperation` |
| `com.safjnest.lol.model.status.RedisMetrics` | `com.R4B.AB.DTO.status.RedisMetrics` |
| `com.safjnest.lol.model.status.SystemMetrics` | `com.R4B.AB.DTO.status.SystemMetrics` |
| `com.safjnest.lol.model.summoner.Mastery` | `com.R4B.AB.DTO.summoner.Mastery` |
| `com.safjnest.lol.model.summoner.Rank` | `com.R4B.AB.DTO.summoner.Rank` |
| `com.safjnest.lol.model.summoner.Summoner` | `com.R4B.AB.DTO.summoner.Summoner` |
| `com.safjnest.lol.model.summoner.SummonerLeaderboard` | `com.R4B.AB.DTO.summoner.SummonerLeaderboard` |
| `com.safjnest.lol.model.summoner.SummonerOverview` | `com.R4B.AB.DTO.summoner.SummonerOverview` |
| `com.safjnest.lol.model.summoner.SummonerView` | `com.R4B.AB.DTO.summoner.SummonerView` |
| `com.safjnest.spring.dto.LolApiError` | `com.R4B.AB.DTO.LolApiError` |

## Chiusura acquisizione SDK → R4B

Il gateway converte dentro `com.safjnest.r4j`; firme e componenti annidati
consumati da bot/servizi/tracker/codec sono indipendenti dall'SDK.
`RiotMatchData` / `RiotTimelineData` descrivono acquisizione raw: il Match
analizzato rimane il solo `com.R4B.AB.DTO.match.Match`. Summoner e mastery
vengono convertiti nei modelli canonici, senza crearne un alias pubblico.
La fixture SDK/storage verifica anche le forme raw storiche.

| Tipo origine/annidato | Tipo indipendente |
|---|---|
| `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard` | `com.R4B.LOL.LeagueShard` |
| `no.stelar7.api.r4j.basic.constants.api.regions.RegionShard` | `com.R4B.LOL.RegionShard` |
| `no.stelar7.api.r4j.basic.constants.types.KillType` | `com.R4B.LOL.KillType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.BuildingType` | `com.R4B.LOL.BuildingType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.EventType` | `com.R4B.LOL.EventType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameModeType` | `com.R4B.LOL.GameModeType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` | `com.R4B.LOL.GameQueueType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.GameType` | `com.R4B.LOL.GameType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` | `com.R4B.LOL.LaneType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LeaguePositionType` | `com.R4B.LOL.LeaguePositionType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.LevelUpType` | `com.R4B.LOL.LevelUpType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MapType` | `com.R4B.LOL.MapType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType` | `com.R4B.LOL.MatchlistMatchType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MonsterSubType` | `com.R4B.LOL.MonsterSubType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.MonsterType` | `com.R4B.LOL.MonsterType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.RoleType` | `com.R4B.LOL.RoleType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.SpellSlotType` | `com.R4B.LOL.SpellSlotType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.SummonerSpellType` | `com.R4B.LOL.SummonerSpellType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` | `com.R4B.LOL.TeamType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType` | `com.R4B.LOL.TierDivisionType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TierType` | `com.R4B.LOL.TierType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TimelineDamageType` | `com.R4B.LOL.TimelineDamageType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.TowerType` | `com.R4B.LOL.TowerType` |
| `no.stelar7.api.r4j.basic.constants.types.lol.WardType` | `com.R4B.LOL.WardType` |
| `no.stelar7.api.r4j.pojo.lol.league.LeagueEntry` | `com.R4B.AB.DTO.riot.LeagueEntry` |
| `no.stelar7.api.r4j.pojo.lol.league.LeagueItem` | `com.R4B.AB.DTO.riot.LeagueItem` |
| `no.stelar7.api.r4j.pojo.lol.league.MiniSeries` | `com.R4B.AB.DTO.riot.MiniSeries` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan` | `com.R4B.AB.DTO.riot.ChampionBan` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.ChampionStats` | `com.R4B.AB.DTO.riot.ChampionStats` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.DamageStats` | `com.R4B.AB.DTO.riot.DamageStats` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch` | `com.R4B.AB.DTO.riot.RiotMatchData` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline` | `com.R4B.AB.DTO.riot.RiotTimelineData` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant` | `com.R4B.AB.DTO.riot.MatchParticipant` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.MatchPerks` | `com.R4B.AB.DTO.riot.MatchPerks` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam` | `com.R4B.AB.DTO.riot.MatchTeam` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.ObjectiveStats` | `com.R4B.AB.DTO.riot.ObjectiveStats` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection` | `com.R4B.AB.DTO.riot.PerkSelection` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle` | `com.R4B.AB.DTO.riot.PerkStyle` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.StatPerk` | `com.R4B.AB.DTO.riot.StatPerk` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineDamageData` | `com.R4B.AB.DTO.riot.TimelineDamageData` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrame` | `com.R4B.AB.DTO.riot.TimelineFrame` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent` | `com.R4B.AB.DTO.riot.TimelineFrameEvent` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineParticipantFrame` | `com.R4B.AB.DTO.riot.TimelineParticipantFrame` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelineParticipantIdentity` | `com.R4B.AB.DTO.riot.TimelineParticipantIdentity` |
| `no.stelar7.api.r4j.pojo.lol.match.v5.TimelinePosition` | `com.R4B.AB.DTO.riot.TimelinePosition` |
| `no.stelar7.api.r4j.pojo.lol.shared.BannedChampion` | `com.R4B.AB.DTO.riot.BannedChampion` |
| `no.stelar7.api.r4j.pojo.lol.shared.BaseSpellData` | `com.R4B.AB.DTO.riot.BaseSpellData` |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameCustomizationObject` | `com.R4B.AB.DTO.riot.SpectatorGameCustomizationObject` |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo` | `com.R4B.AB.DTO.riot.SpectatorGameInfo` |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant` | `com.R4B.AB.DTO.riot.SpectatorParticipant` |
| `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorPerks` | `com.R4B.AB.DTO.riot.SpectatorPerks` |
| `no.stelar7.api.r4j.pojo.lol.spectator.v5.Observer` | `com.R4B.AB.DTO.riot.Observer` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.Block` | `com.R4B.AB.DTO.riot.Block` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.BlockItem` | `com.R4B.AB.DTO.riot.BlockItem` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.Info` | `com.R4B.AB.DTO.riot.Info` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.Passive` | `com.R4B.AB.DTO.riot.Passive` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.Recommended` | `com.R4B.AB.DTO.riot.Recommended` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.Skin` | `com.R4B.AB.DTO.riot.Skin` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion` | `com.R4B.AB.DTO.riot.StaticChampion` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampionSpell` | `com.R4B.AB.DTO.riot.StaticChampionSpell` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampionStats` | `com.R4B.AB.DTO.riot.StaticChampionStats` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.item.InventoryDataStats` | `com.R4B.AB.DTO.riot.InventoryDataStats` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.item.Item` | `com.R4B.AB.DTO.riot.Item` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.shared.Gold` | `com.R4B.AB.DTO.riot.Gold` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.shared.Image` | `com.R4B.AB.DTO.riot.Image` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.shared.LevelTip` | `com.R4B.AB.DTO.riot.LevelTip` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.shared.MetaData` | `com.R4B.AB.DTO.riot.MetaData` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.shared.SpellVars` | `com.R4B.AB.DTO.riot.SpellVars` |
| `no.stelar7.api.r4j.pojo.lol.staticdata.summonerspell.StaticSummonerSpell` | `com.R4B.AB.DTO.riot.StaticSummonerSpell` |
| `no.stelar7.api.r4j.pojo.shared.RiotAccount` | `com.R4B.AB.DTO.riot.RiotAccount` |

## Helper e operazioni dopo la separazione

| Responsabilità | Proprietario | Consumatori/conversione |
|---|---|---|
| ID, alias, lookup, prettyName, received/canonical game queue | `com.R4B.LOL.GameQueueType` | Parametri API, filtri, tracker, rank, bot; utility applicativa delega la semantica |
| Rappresentazione statistiche | `com.R4B.LOL.CanonicalQueue` | ProfileStatistics/ProfileMatchups e analizzatori; mantiene tutti i 126 mapping baseline |
| Rank composition/decomposition/MMR/order/boundaries | `TierType`, `TierDivisionType` R4B | RankService, competitive/leaderboard e bot; formule baseline immutate |
| Platform/Match/Account routing e cache-region | `LeagueShard`, `RegionShard` R4B | Gateway e servizi; shard abilitate e scelte Discord restano applicazione |
| Lane/role/playables/duo | `LaneType`, `RoleType` R4B | API, statistiche, matchup, bot; lane e role separati |
| Encode/decode chiavi, dash-list/Base64, KDA e numeri | `FilterCodec`, `KdaUtils`, `NumberUtils`, `BuildValues` R4B | DTO/producer/consumer; stessi formati storici |
| Default patch/season/split e decoder legacy parziali | `lol.service.FilterDefaults` | API/comandi/cache/build; i DTO non caricano runtime per scegliere default |
| Letture/scritture, dirty tracking Summoner/Match | `nosql.writer.SummonerWriter`, `MatchWriter` | Composizione dello stesso DTO canonico; nessuna ereditarietà persistente nel comune |
| Profile leaf/statistics accumulators | `LeafStatsAccumulator`, `ProfileLeafAccumulator`, `ProfileStatisticsAccumulator` | Producer Spring e analyzer; dati finali R4B |
| Activity/matchups aggregazione | `ProfileActivityAnalyzer`, `ProfileMatchupsAnalyzer` | ProfileAnalyzer/ProfileService; accumulatori e bucket privati esclusi |
| Profile/livegame composizione e static lookup | `SummonerProjection`, `LiveGameProjection` | SummonerService/ProfileService/LeaderboardService; DTO finali R4B |
| Build/statistics/rune parsing storage | `BuildCodec`, `ChampionStatisticsCodec`, `RuneSignatureCodec` | Mongo/cache/calcolo producer; forma dati rune in R4B |
| HTTP JSON | `com.R4B.AB.DTO.TransportJson` | Spring configura lo stesso mapper; non usa il profilo BSON/storage |
| SDK fetch/conversion/credenziali | `com.safjnest.r4j.RiotGateway` | Adapter locale con firme R4B; nessun nuovo client HTTP |
| Emoji, opzioni, testi, embed/layout e deployment shard | Bot e utility applicative | Mantiene la presentazione esistente, con fixture di caratterizzazione |

I consumatori comprendono controller/parser HTTP, servizi, modelli interni,
Mongo/repository/codec, Redis/cache, scheduler, tracker, comandi, messaggi e
test. Compilazione completa e gate senza SDK verificano il collegamento
dell'intera chiusura, oltre alla tabella nominale baseline.
