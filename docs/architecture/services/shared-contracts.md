# Contratti condivisi R4B

Stato: tranche accettata da ADR-0018, baseline implementativa `8a88645e`.
La proposta generale di separazione ADR-0016 rimane tale oltre Hive/R4B.
Inventario operativo: [r4b-inventory.md](r4b-inventory.md); evidenze e limiti
sono registrati in [r4b-validation.md](r4b-validation.md).

## Criterio di inclusione

Un tipo è comune se descrive una richiesta o risposta scambiata tra servizi,
oppure un valore necessario a quel contratto. Non serve che sia indipendente
dal dominio LoL: è il sistema job/code che deve essere indipendente anche dal
dominio. Un record usato solo per calcolare una build non diventa un contratto
per il solo fatto di essere un record.

I nomi canonici rimangono `Summoner`, `Rank`, `Mastery`, `SummonerView`,
`SummonerLeaderboard`, `Match`, `Participant`, `MatchResult`, ecc. Si estrae
il proprietario esistente, senza introdurre copie `SummonerDto`, `MatchDto`,
`SummonerRank`, `ChampionMastery` o alias mantenuti in parallelo.

I package definitivi sono `com.R4B.api.dto`, con sottopackage per ambito,
e `com.R4B.lol` per enum, costanti e helper puri. Solo `R4B` mantiene le maiuscole; `api.dto` e `lol` sono minuscoli (scelta utente 2026-10-12). Ogni classe di risposta comprende i propri componenti annidati che
sono effettivamente serializzati. I componenti operativi annidati devono invece
essere spostati nel servizio produttore.

## Catalogo dei contratti finali

| Risposta/ambito | Radici canoniche | Dipendenze di dati da includere |
|---|---|---|
| Profile per PUUID/Riot ID e search | `SummonerView` | `Summoner`, `Rank`, `SummonerOverview`, `Mastery`, `SummonerOverview.Champion`, statistiche profilo |
| Overview profilo | `SummonerOverview` | `ProfileStatistics`, `CanonicalQueue`, `ProfileLeafStats`, base `LeafStats`; masteries e mappa champion |
| Match list | `MatchPage`, `MatchResult` | `Participant` e valori/record del risultato secondo la firma canonica; `RankProgress` dove presente |
| Match detail | `Match` | `Participant`, `RankProgress`, bans, statistiche e strutture effettivamente esposte, senza stato `AbstractEntity` |
| Live game | `LiveGame` | Componenti partecipanti/bans, overview, rune e altri dati finali; nessun `SpectatorGameInfo` o `SpectatorParticipant` SDK |
| Rank history | `RankHistory`, `RankHistoryMatch`, `RankHistoryMetadata` | `RankHistoryView`, `RankProgress` e componenti serializzati |
| Activity | `ProfileActivity` | `Coverage`, `Summary`, `Heatmap`, finestre, righe giorno/ora/queue, sessioni e insight finali; escludere accumulatori/bucket |
| Matchups profilo | `ProfileMatchups` | `ProfileMatchupLeaf`, `ProfileLeafStats`, `CanonicalQueue`; escludere accumulatore e parser timeline |
| Records profilo/globali | `ProfileRecordPage`, `RecordsOverview`, `RecordPage`, `ProfileRecord` | `RecordMetric`, `RecordOwner`, componenti annidati delle pagine |
| Champion page | `ChampionView`, `ChampionStatistics`, `Build` | Record overview/lane/matchup/synergy/power-curve/trend finali; opzioni build e forma rune |
| Champion tier list | `ChampionTierList` | Record finali statistics/champion/role/relation, secondo il modello esistente |
| Leaderboard/distribution/top regions | `LeaderboardPage`, `SummonerLeaderboard`, `LeaderboardDistribution` | Lo stesso `SummonerView` dei profili e componenti distribuzione |
| Indexables | `ProfileIndexable`, `ChampionIndexable` | Soli campi URL/identità/filtri del risultato |
| Metadata e parametri | `ResponseMetadata`, `ResponseMetadata.Pagination`, parte dati di `Filter`, `ActivityFilter` | Enum/value types e parametri di query necessari; separare default runtime e storage keys |
| Esito applicativo | `ApiResult<T>` | `READY`, `PARTIAL`, `PENDING`, `NOT_FOUND`; non introdurre un envelope JSON nuovo |
| Status operativo | `BotStatus`, `LeagueMetrics`, `JvmMetrics`, `SystemMetrics`, `RedisMetrics`, `MongoMetrics` e componenti `Mongo*` | Dati finali; raccolta delle metriche esclusa. DTO job estratti in `com.hive.status` (ADR-0017) |
| Errori HTTP | `LolApiError` | Schema `status/code/message/metadata` condivisibile senza dipendenze Spring; mapping e gestione eccezioni restano Spring |

Le associazioni per famiglia sono un inventario iniziale, non la promessa che
ogni classe annidata sia già un DTO puro. Il manifest classifica tutti i file
model; prima di ogni estrazione serve verificare la chiusura effettiva dei tipi,
compresi generics, ereditarietà, annotazioni e campi annidati.

L'export `/api/lol/ai/training` è un caso distinto: oggi è streaming di mappe
Mongo, non un DTO Java finale. Resta in Spring; un contratto tipizzato comune
si aggiungerà solo con un consumatore e una specifica concreta, preservando lo
streaming e senza caricare l'intero dataset in una lista.

## Esempio: profilo

La forma attuale di `SummonerView` è:

```text
summoner: Summoner
ranks: Map<queue, Rank>
overview: SummonerOverview
metadata: ResponseMetadata

overview.statistics: ProfileStatistics
overview.masteries: List<Mastery>
overview.champions: Map<championId, {name, image}>
```

Nella baseline non bastava estrarre `SummonerView`: `Summoner` conteneva
operazioni persistenti, `SummonerOverview.from` consultava `ChampionUtils` e
`ProfileStatistics` calcolava aggregati. La separazione ora usa writer per
composizione, `SummonerProjection` e accumulatori nel produttore. La sequenza corretta è:

1. Conservare il tipo canonico e i campi finali.
2. Portare scritture, accumulatori, lookup di dati statici e composizione al
   proprietario Spring; adapter R4J converte i soli dati del suo confine.
3. Estrarre il modello e tutta la sua chiusura di dati dopo aver rimosso le
   dipendenze operative. Evitare un entity model pubblico parallelo con lo stesso significato.

L'ereditarietà da `AbstractEntity` deve cessare sul contratto estratto; update,
upsert e dirty tracking restano in Spring su infrastruttura privata del writer.
La migrazione locale conserva queste operazioni nel produttore, senza creare
un secondo modello pubblico o una gerarchia parallela di entity/DTO.

`userId`, tracking e altri campi oggi non pubblici non diventano automaticamente
campi HTTP. Link/unlink/tracking del bot richiedono richieste al proprietario
e un contratto interno esplicito; non si aggiungono al profilo pubblico per comodità.

## Esempio: build

`Build` è il risultato comune; restano Spring `ChampionBuildEngine`,
`ChampionBuildProvider`, `ChampionBuildData`, `BuildSignature`, accumulatori,
batch Mongo e lettura timeline. La shape condivisa comprende almeno i record
`Option`, `CoreBuildOption`, `RuneOption`, `SkillOrderOption`, `SkillTiming`
e gli altri componenti serializzati del modello canonico.

Le chiavi JSON attuali includono `games`, `wins`, `winrate`, `coreBuilds`,
`coreItems`, `starters`, `boots`, `supportItems`, `roleBoundItems`, `slots`,
`runes`, `summonerSpells`, `skillOrders`, `prismatics`, `augments`.
Le annotazioni `@JsonProperty` rendono diversi nomi Java differenti dai nomi
JSON: non vanno rimossi o rinominati durante l'estrazione.

`RuneSignature` costituisce un'eccezione all'esclusione del package champion:
è il tipo di `RuneOption.configuration` e compare anche nei livegame.
I suoi dati `primaryTree`, `keystone`, `primaryRunes`, `secondaryTree`,
`secondaryRunes`, `statShards` vanno nel comune. Parsing dei dati acquisiti e chiavi operative di aggregazione restano nel
lato produttore, con un solo owner; nel comune rimane la forma dati serializzata.

La decisione non implica condividere tutto `BuildUtils`: oggi dipende anche
da `LeagueHandler` e tipi statici R4J. Convenienze pure eventualmente richieste
dal bot possono essere mantenute o estratte singolarmente; nessun calcolo della
build viene trasferito al consumatore.

## Parametri, enum e serializzazione

Molti contratti espongono `LeagueShard`, `GameQueueType`, `LaneType`,
`TierType`, `TierDivisionType`, `TeamType`, `GameModeType`, `GameType`, `MapType`.
Un comune che li importasse direttamente avrebbe ancora una dipendenza dal SDK.

ADR-0018 assegna gli enum/value types a `com.R4B.lol`, con gli stessi valori
JSON accettati oggi, e conversioni SDK soltanto in R4J. Una dipendenza SDK
residua fuori dall’integrazione R4J impedisce il completamento della tranche. Non usare ordinali
sul wire e non confondere platform shard (`EUW1`) con routing region (`EUROPE`).
Alias queue, queue canonica persistita e `CanonicalQueue` delle statistiche
hanno significati distinti e vanno preservati, senza inventare un unico enum
che li appiattisca. La gestione di valori futuri/sconosciuti richiede una decisione
esplicita prima del port, mantenendo le validazioni HTTP vigenti.

`Filter` oggi contiene stato, default patch/season e codifica delle chiavi.
Il comune riceve il dato necessario alle richieste/metadata; non deve calcolare
la patch corrente caricando R4J o dipendere da slash-command choices. Le codifiche
pure condivise sono ammesse solo se i servizi devono interpretarle entrambi.
Le chiavi interne Mongo/Redis restano responsabilità del proprietario.

Il codec comune usa JSON e non BSON. Jackson annotations sono ammissibili sui
DTO; `org.bson`, driver Mongo, JDA, R4J, Spring e Jedis sono esclusi dal modulo
contratti target. `JsonCodec.toDocument/fromDocument` rimangono nella parte dati.

Serve un profilo JSON di trasporto comune che riproduca il JSON HTTP attuale.
Il profilo di storage è distinto dove necessario: `Build.filter` e
`ChampionStatistics.filter` sono nascosti da `LolApiConfig` nel payload HTTP,
ma sono usati dai codec/cache interni. Non basta trasferire il mapper di storage
nel client del bot: potrebbe esporre campi interni.

## Invarianti pubbliche

- Stessi nomi, tipi, enum stringa, null/assenze, array e mappe; nessun `data` wrapper.
- Metadata root `{pagination,lastUpdate,refresh,filter}`; search/indexables sono
  array; `LiveGame` mantiene `notInGame`; status mantiene il suo root operativo.
- `READY`/`PARTIAL` sono `200`; `PENDING` è l'errore `202`; `NOT_FOUND` è `404`.
  Una `202` non deve essere deserializzata come risposta riuscita.
- Profile con identità disponibile e statistiche filtrate mancanti/stale conserva
  la semantica partial `200` e refresh; l'inizializzazione dell'identità usa `202`.
- `riotId` è `String`, PUUID è la chiave identità, match ID è completo (`EUW1_...`).
- Il dato sconosciuto non diventa zero, unranked o lista vuota per mascherare un errore.
- Ranking matchup/synergy resta una proiezione: cache/aggregati grezzi e valori
  arricchiti HTTP restano distinti. Il bot conserva la semantica e la selezione
  attuale delle righe; un client HTTP non deve cambiare l'embed incidentalmente.
- Presentazione Discord invariata: campi, ordine, testi, formattazione e layout.

## Confine lol-service ↔ S4B (integrazione R4J)

R4J è un dettaglio SDK di S4B; Spring non lo importa. S4B acquisisce account,
summoner, league entries, masteries, spectator, match-list, match/timeline e
dati statici. Le route Riot-shaped e il mapping dei risultati ai DTO R4B sono
specificati in [riot-endpoint-proxy-spec.md](riot-endpoint-proxy-spec.md).

Richiesta HTTP: route e argomenti Riot originali, routing scope nel host e
priorità interna `X-S4B-Priority`. Risposta: DTO R4B compatibile con la route,
senza `CompletableFuture`, SDK POJO, `Job`, driver o cursor. `lol-service`
mantiene i servizi di dominio e converte i dati acquisiti nei modelli persistiti
o nelle proiezioni. Non riusare `Match` come se fosse il match raw Riot.

Il contratto è request/response: durante un rate limit S4B lascia pendere il
lavoro e il proxy Spring mantiene aperto lo scambio HTTP. I dettagli di retry,
parametri e mapping restano vincolati alla versione R4J e sono gate di controllo
prima dell'implementazione.

## Client HTTP lol-service ↔ S4B

Il client inter-servizio appartiene a `lol-service`, non a R4B/Hive. Solo il
client conosce base URL e autenticazione S4B; un catch-all inoltra il percorso
Riot senza mantenere un secondo catalogo di route. I metodi domain tipizzati
usano le classi R4B già esistenti. `HttpUtils` attuale offre GET generiche,
timeout e gestione errori a `null`; non è già il client inter-servizio. Il
proxy conserva gli status/body HTTP previsti e rimuove header non inoltrabili;
non ritenta automaticamente chiamate senza idempotenza.

## Gate di estrazione

Il contratto è estraibile quando compila senza dipendenze dei servizi, il JSON
prodotto e consumato supera fixture di compatibilità e il bot usa lo stesso
tipo canonico. Chi produce il risultato resta il solo owner del calcolo.
Dettagli e ordine dei task nel [piano](migration-plan.md).

Hive rimane sotto ADR-0017 e i suoi status generici sono riutilizzati senza
copie. R4B è una tranche autonoma ADR-0018; processi separati, client HTTP,
bootstrap applicativi indipendenti e trasporto tra servizi restano fuori scope.

## Dati temporanei compatibili nel modello unico

I container derivati in memoria `ProfileStatistics.total/queueStats/laneStats/
championStats` e `ProfileLeafStats.reference` rimangono dati esclusi dal JSON
con `@JsonIgnore`, per conservare il consumo bot/produttore dello stesso modello
canonico. Il loro calcolo vive negli accumulatori del produttore; non implicano
dipendenze a servizi, SDK, driver o bootstrap in R4B. Non si introducono copie
pubbliche dei modelli né si pubblicano questi campi nel payload HTTP/storage.
