# DomainEvent bus + match → record incrementale

## Obiettivo

Quando un match entra in `MatchService.insert`, i `ProfileRecord` dei
participant si aggiornano in modo **incrementale** (delta) tramite un bus
eventi JDA-style, senza full re-scan della season. Le stats/matchups restano
fuori da questo step, ma l'infrastruttura eventi è già pronta per loro.

## Scope step 1

Solo records. Stats/matchups/champion = step futuri.

## Pre-work (CodeGraph + skill)

1. `codegraph status` → se stale `codegraph sync`.
2. `codegraph explore ProfileRecordService` + `codegraph explore MatchService`
   + `codegraph explore MongoDB` (blast radius).
3. `codegraph impact ProfileRecordService.generate` (caller da toccare).
4. `codegraph impact MatchService.insert` (hook point).
5. `codegraph impact MongoDB.upsertProfileRecords` (vicino al nuovo
   `bulkUpsertIfBetter`).
6. Caricare skill `beebot-handbook` per checklist §5.12 (test) + §7 (gate).
7. ADR check: nessun ADR esistente su eventi record. Procedo, refactor
   interno al dominio.

## File nuovi

### `lol/event/LeagueEventListener.java`

```java
public interface LeagueEventListener {
    default void onMatchCreated(Match match) {}
    default void onMatchTracked(Match match) {}
    default void onSummonerRefreshed(String puuid, LeagueShard shard) {}
}
```

### `lol/event/DomainEvent.java`

```java
public enum DomainEvent {
    MATCH_CREATED      { public void dispatch(LeagueEventListener l, Object p) { l.onMatchCreated((Match) p); } },
    MATCH_TRACKED      { public void dispatch(LeagueEventListener l, Object p) { l.onMatchTracked((Match) p); } },
    SUMMONER_REFRESHED { public void dispatch(LeagueEventListener l, Object p) { SummonerKey k = (SummonerKey) p; l.onSummonerRefreshed(k.puuid(), k.shard()); } };

    private final List<LeagueEventListener> listeners = new CopyOnWriteArrayList<>();
    private static final ExecutorService DISPATCH = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "domain-event-dispatch");
        t.setDaemon(true);
        return t;
    });

    public abstract void dispatch(LeagueEventListener listener, Object payload);

    public void subscribe(LeagueEventListener listener) { listeners.add(listener); }

    public void publish(Object payload) {
        DISPATCH.execute(() -> {
            for (LeagueEventListener listener : listeners) {
                try { dispatch(listener, payload); }
                catch (RuntimeException e) { BotLogger.error("DomainEvent " + name() + " listener failed", e); }
            }
        });
    }
}
```

### `lol/event/SummonerKey.java`

```java
public record SummonerKey(String puuid, LeagueShard shard) {}
```

### `lol/service/MatchRecordAnalyzer.java`

Estrae da `ProfileRecordAnalyzer` il cuore "match → record". Stesso codice
di `ProfileRecordAnalyzer.Accumulator.accept(Match)` ma **stateless**: per
ogni participant del match produce TUTTI i record candidati. Niente
`EnumMap`, niente `before` — il confronto col record corrente lo fa Mongo.

```java
public final class MatchRecordAnalyzer {
    public static List<ProfileRecord> extract(Match match) { ... }
}
```

### `src/test/java/com/safjnest/lol/service/ProfileRecordServiceTest.java`

- `processMatch` con match mock → 10 participant × 18 metriche ≈ 180 update.
- `processMatch` con match già vincente → solo update che passano il filtro.
- `processMatch` con match non vincente (value < current) → 0 update.
- `processMatch` ripetuto 2 volte con stesso match → idempotente.
- `onSummonerRefreshed` → chiama `generate` (full re-scan, invariato).

Naming: `beebot_test` come da HANDBOOK.

## File modificati

### `MongoDB.java` — nuovo `bulkUpsertIfBetter`

Posizione: vicino a `upsertProfileRecords` (linea 2535).

Per ogni `ProfileRecord` candidato, `UpdateOneModel` con pipeline update:

```
[
  { $set: {
      puuid: { $ifNull: ["$puuid", puuid] },
      filterKey: { $ifNull: ["$filterKey", filterKey] },
      metric: { $ifNull: ["$metric", metric.name()] },
      value: { $cond: [ { $or: [
          { $eq: ["$value", null] },
          { $cond: [ { $eq: [order, "HIGHEST"] }, { $gt: ["$value", newValue] }, { $lt: ["$value", newValue] } ] },
          { $and: [ { $eq: ["$value", newValue] }, { $cond: [ { $eq: [order, "HIGHEST"] }, { $lt: ["$occurredAt", occurredAt] }, { $gt: ["$occurredAt", occurredAt] } ] } ] },
          { $and: [ { $eq: ["$value", newValue] }, { $eq: ["$occurredAt", occurredAt] }, { $cond: [ { $eq: [order, "HIGHEST"] }, { $lt: ["$matchId", matchId] }, { $gt: ["$matchId", matchId] } ] } ] }
      ] }, newValue, "$value" ] },
      score: { $cond: [...stesso filtro..., newScore, "$score"] },
      matchId: { $cond: [...stesso filtro..., matchId, "$matchId"] },
      occurredAt: { $cond: [...stesso filtro..., occurredAt, "$occurredAt"] },
      championId: { $cond: [...stesso filtro..., championId, "$championId"] },
      region: { $cond: [...stesso filtro..., region, "$region"] },
      team: { $cond: [...stesso filtro..., team, "$team"] },
      actorPuuid: { $cond: [...stesso filtro..., actorPuuid, "$actorPuuid"] },
      gameShared: { $cond: [...stesso filtro..., gameShared, "$gameShared"] },
      mmr: { $cond: [...stesso filtro..., mmr, "$mmr"] },
      lastUpdate: NOW
  }}
]
```

Con `UpdateOptions.upsert(true)`. Una sola `bulkWrite` su `profileRecords()`
con N `UpdateOneModel`. Restituisce `int updated` (conteggio da
`BulkWriteResult.getModifiedCount()` + upserts).

**Alternativa semplificata** (scelta per debug facilitato): lettura Java di
`findProfileRecords(puuid, filter)` per ogni puuid coinvolto, confronto in
Java, `upsertProfileRecords` con i vincitori. Stesso `upsert` esistente,
niente nuova pipeline Mongo. ~1 read per puuid per match.

### `ProfileRecordService.java`

- Aggiunge `static {}` che si auto-registra su `MATCH_CREATED` e
  `SUMMONER_REFRESHED`.
- Nuovo `onMatchCreated(Match match)`: accoda job su
  `ComputeScheduler.MATCH_INGEST` (nuova coda) che chiama `processMatch(match)`.
- Nuovo `processMatch(Match match)`: chiama `MatchRecordAnalyzer.extract(match)`
  + `MongoDB.bulkUpsertIfBetter(candidates)` + invalidation cache
  `SUMMONER_OVERVIEW` per i puuid coinvolti.
- Nuovo `onSummonerRefreshed(puuid, shard)`: accoda full re-scan (chiama
  `generate` esistente).

```java
public final class ProfileRecordService implements LeagueEventListener {
    private static final ProfileRecordService INSTANCE = new ProfileRecordService();
    static {
        DomainEvent.MATCH_CREATED.subscribe(INSTANCE);
        DomainEvent.SUMMONER_REFRESHED.subscribe(INSTANCE);
    }

    @Override public void onMatchCreated(Match match) { ... }
    @Override public void onSummonerRefreshed(String puuid, LeagueShard shard) { ... }

    public void processMatch(Match match) { ... }
    public boolean generate(String puuid, LeagueShard shard, Filter filter) { ... } // esistente
}
```

### `MatchService.java` (linea 115)

Dopo `boolean inserted = MongoDB.insertMatch(match);` e prima del return, se
`inserted`:

```java
if (inserted) DomainEvent.MATCH_CREATED.publish(match);
```

Una riga, fire-and-forget, async.

### `ComputeScheduler.java`

Aggiunge la coda `MATCH_INGEST` per i job record. Stesso pattern delle
altre code per-shard.

### `App.java` o nuovo `LoLBootstrap.java`

Forza il caricamento di `ProfileRecordService` al boot (riferimento statico)
per garantire che lo `static {}` venga eseguito. Niente init esplicito di
registry.

## Cache invalidation

Dopo `bulkUpsertIfBetter`, `RedisClient.delete(SUMMONER_OVERVIEW.of(region,
shard, puuid))` per ogni puuid coinvolto (ottenibile dai candidati). Stesso
pattern per la cache globale records se il record è shared.

## Documenti da aggiornare

- `docs/HANDBOOK.md` sezione "match ingest" → ora pubblica `MATCH_CREATED`.
- `docs/architecture/README.md` → schema eventi + diagramma.
- `docs/mongo/README.md` (o equivalente) → nuovo `bulkUpsertIfBetter`, indici
  invariati (`profile_records {puuid, filterKey, metric}` unique resta valido).
- `CHANGELOG.md` → voce nuova "match → record incrementale via DomainEvent".
- ADR nuovo (opzionale): `docs/architecture/adr/0007-domain-event.md` se il
  pattern prende piede.

## Rischi / punti aperti

- **Tie-break occurredAt/matchId**: gestito in pipeline Mongo. Se complesso,
  fallback lettura Java.
- **Backpressure**: 1k match = 1k job su `MATCH_INGEST`. Stima ~5-10s/100
  match in bulk → 1-2 min per 1k. Accettabile. Se serve tetto, `Semaphore` o
  bucket per gameId.
- **Caricamento static {}**: la JVM carica `ProfileRecordService` solo al
  primo uso. Workaround: riferimento in `App.start()` o `LoLBootstrap.register()`.
- **Idempotenza**: pipeline con `$cond` su `value {op}` è idempotente. ✓
- **Record shared (team)**: `gameShared=true` con actorPuuid diverso.
  Confronto resta per `puuid+filterKey+metric`, l'actorPuuid aggiornato va nel
  `$set` condizionale. ✓
- **Race con `generate` esistente**: se `onSummonerRefreshed` parte mentre un
  `processMatch` è in coda, `generate` fa full re-scan dopo, sovrascrive
  correttamente. Stesso shard, stessa coda → serializzati. ✓
- **Cache Redis**: `SUMMONER_OVERVIEW` per ogni puuid. Se match coinvolge 10
  puuid, 10 delete. Trascurabile.

## Step di esecuzione

1. CodeGraph (vedi pre-work).
2. Skill `beebot-handbook` checklist.
3. Creare `LeagueEventListener`, `SummonerKey`, `DomainEvent`.
4. Creare `MatchRecordAnalyzer.extract(Match)`.
5. Aggiungere `MongoDB.bulkUpsertIfBetter` (versione pipeline con `$cond` o
   alternativa lettura Java).
6. Modificare `ProfileRecordService` (implements + onMatchCreated +
   processMatch + onSummonerRefreshed + static register).
7. Hook `MATCH_CREATED.publish` in `MatchService.insert`.
8. Aggiungere `MATCH_INGEST` a `ComputeScheduler` se serve.
9. `LoLBootstrap.register()` per forzare caricamento classi.
10. Test `beebot_test` in `ProfileRecordServiceTest.java`.
11. Update docs (HANDBOOK, architecture, mongo) + CHANGELOG.
12. `git status --short` + `git diff --check` + `explain("executionStats")`
    su `bulkUpsertIfBetter`.

## Step successivi (post step 1)

- `MATCH_TRACKED` hook in `Tracker.trackMatch` → `ChampionService.onMatchTracked`
  per champion stats delta.
- `SUMMONER_REFRESHED` hook in `SummonerService.refreshProfile` → full
  re-scan per `ProfileService` (stats/activity/matchups) e `CompetitiveService`.
- Stats/matchups **delta incrementali** (nuovo task):
  `MongoDB.applyMatchStatsDelta(match)` con lettura Java + `$inc` su campi
  somma e `$max` su long/longest.
- Activity/matchups delta.
- Eventuale ADR formale per il pattern DomainEvent se usato estesamente.
