# Piano di implementazione Arena

Stato aggiornato il 2026-10-14: contratto CHERRY implementato; vedi schema.md. Le descrizioni 5/5 qui sotto sono storiche. Le fasi del 2026-10-10 qui sotto documentano il precedente stato interno 4/4; il nuovo contratto è in contracts.md. Round-trip Mongo operativo, sizing rappresentativo e rollout restano aperti.

## Fase 0 — Contratto: completata

- Usare [contracts.md](contracts.md) come requisito attivo.
- Usare [schema.md](schema.md) come schema target.
- Conservare la persistenza condivisa in champion_builds/build.arena e l'ownership di provider, service e scheduler.
- Il 2026-10-14 la richiesta approvata estende l’endpoint champion esistente a CHERRY; nessun restyling delle presentazioni.

## Fase 1 — Parser e classi: implementata

`MongoDB` carica `match_events` in batch e valorizza `Match.events` con un solo `JSONObject`; `eventData` non contiene una seconda copia. `ArenaGameParser` legge gli `item_events` di quel payload. Il parser ha un'estrazione propria per preservare l'ordine degli acquisti storici e non usa `ChampionBuildTimelineUtils`, che ricostruisce l'inventario finale della build standard. `ArenaItemCatalog` contiene la classificazione patch-aware.

- Ridurre il primo-Prismatico alla prima evidenza timeline classificata, escluso 220007; usare item0..item5 come fallback.
- Far produrre al parser i soli dati richiesti: esito, boots, primo Prismatico, leggendari ordinati e augments con posizione.
- Ordinare gli eventi per timestamp e indice originale; mantenere attribuzione, deduplicazione e annullamento del solo acquisto corrispondente immediatamente precedente.
- Usare il timestamp solo per l'ordinamento. Arena persiste l'ID dell'acquisto e la sua posizione nella sequenza, non il timestamp.
- Rimuovere i reason code e la ricostruzione contabile non più usati.
- Riunire la classificazione patch-aware in ArenaItemCatalog.

## Fase 2 — Aggregatore e payload: implementata

- Semplificare ArenaChampionAnalyzer in un solo accumulatore per overall, core, item per posizione, Prismatics e augments.
- Semplificare ArenaBuildData con record interni per statistiche e opzioni.
- Codificare le coppie di ID/posizione con `longKey` e usare mappe FastUtil `Long2ObjectOpenHashMap`; gli ID singoli usano `Int2ObjectOpenHashMap`.
- Evitare di conservare una `String` per match: mantenere il controllo duplicati con suffissi numerici in `LongOpenHashSet`, raggruppati per piattaforma e lunghezza. `coverage.matches` resta un contatore; rimuovere i set per-game che duplicavano la deduplicazione già fatta dal parser.
- Eliminare AnchorKind, core augment, Context, step progressivi, Choices con più popolazioni, builds complete per sequenza, posizioni Prismatics e le relative mappe.
- Aggiornare lo schema a 4/4. Nessun adattatore per lo schema precedente.

## Fase 3 — Wiring esistente: preservato e adeguato

- Riutilizzare ChampionBuildProvider.forEachArenaBatch e MongoDB.forEachChampionArenaMatchBatch con batch limitati.
- Caricare al massimo 100 match e fare il join timeline per gli ID del batch; passare al servizio anche i match senza timeline.
- Rilasciare documenti Mongo, mappe evento e alberi `Match` dopo il callback sincrono; ogni timeline viene materializzata una volta sola e il payload dell'accumulatore non mantiene riferimenti alla timeline.
- Riutilizzare ChampionService.refreshArena e ComputeScheduler.startChampionArena.
- Scrivere solo build.arena e preservare scritture standard, cache, deduplicazione del job e marker standard esistenti.
- Mantenere il progress del job con total, completed, missing e failed tramite il percorso già presente.
- Non aggiungere una collection o un secondo service/provider.

## Fase 4 — Verifica offline: completata

Eseguiti i test focalizzati `ArenaGameParserTest`, `ArenaChampionAnalyzerTest`, `ChampionServiceArenaTest`, `MongoChampionArenaSourceTest` e `MongoChampionArenaPersistenceTest`: 29 test, 0 fallimenti. Coprono eventi/fallback, acquisti annullati, aggregazioni e win rate, match senza timeline, join dei batch, writer e round-trip JSON/BSON locale.

I test usano fixture e store isolati e non eseguono query Mongo. Durante il setup statico sono stati registrati tentativi Redis e fetch patch non disponibili; non hanno causato fallimenti. Questi test non certificano indici, dimensione BSON combinata o heap rappresentativo.

## Gate operativo: da completare

- round-trip e scrittura del sottoalbero 5/5 su MongoDB di test;
- sizing BSON rappresentativo e verifica della headroom del documento combinato;
- explain degli indici e misure di heap sul volume previsto;
- verifica in ambiente test delle scritture standard e Arena in entrambi gli ordini;
- verifica operativa del trigger API CHERRY e della rigenerazione lazy dei vecchi risultati.

Non eseguire query, rebuild, backfill o scritture sul database reale come parte della verifica offline.

## Classi e ownership

Prima della semplificazione la cartella Arena conteneva nove classi di dominio/aggregazione: ArenaGameParser, ParsedArenaGame, ArenaItemCatalog, FirstPrismaticResolver, FirstPrismaticResult, FirstPrismaticResolveType, PrismaticItemClassifier, ArenaChampionAnalyzer e ArenaBuildData.

Risultato: cinque classi. Sono conservate ArenaGameParser, ParsedArenaGame, ArenaItemCatalog, ArenaChampionAnalyzer e ArenaBuildData. Il selettore del primo Prismatico è nel parser e la classificazione patch-aware nel catalogo; FirstPrismaticResolver, FirstPrismaticResult, FirstPrismaticResolveType e PrismaticItemClassifier sono stati rimossi. Le classi shared di caricamento, persistenza e job restano ai rispettivi proprietari.

Dal 2026-10-14 il trigger API e il refresh champion selezionano Arena per CHERRY.
Il join raw è unico per tutte le queue, senza politiche di eligibilità. Il reader
Arena valida schema/aggregation 5/5 e arenaLastUpdate; il vecchio standard a zero
non impedisce il refresh. Core boots+primo Prismatico, nessuno starter, sei posizioni
augment e item globali anche senza core sono coperti dalle fixture di integrazione.

## Verifica integrazione 2026-10-14

Compilazione completa in output temporaneo e 78 test focalizzati offline: zero
fallimenti, errori o skip. Il test ChampionArenaRawFlowTest parte da sole fixture
match/eventi e attraversa il join reale, la matrix, entrambi i writer, i reader
API e il mapper Spring. Poi rimuove solo Arena e verifica il refresh build-only dallo scheduler reale
senza aggiornare le stats. Verifica risultati davvero vuoti pronti senza miss
indefiniti. Copre anche la queue standard ARAM attraverso il join
comune. Store Mongo e cataloghi sono finti, Redis è isolato per questi test.
Sono inoltre verificati i payload precedenti, sei augments con posizioni mancanti,
item globali senza core, role CHERRY rifiutato e JSON/BSON. Nessuna verifica live
su Mongo/Redis/HTTP, nessun indice/explain o rebuild di produzione è stato eseguito.

## Contratto approvato (2026-10-14)

The current contract supersedes v5 descriptions above: see schema.md and
champion-schema.json. CHERRY HTTP flattens Arena into build and groups complete
item combinations within coreBuilds. Full-build `items` include recognized
consumables, including the Prismatic Anvil `220007`, while excluding Prismatic
items. It also exposes six-position augment rankings and global/core Prismatics,
and replaces lane relations with exact subteam
synergies, sizes 2/3, `subteamsById` counts for Arena IDs 1–8 and placement
distribution/average in `overview`. Placement counts do not change synergy
aggregation. Persistence ownership remains build.arena; readers do not require
all eight ID counters or placement buckets to exist and do not reject builds
with old separate `consumables` fields. Present data is served; stale timestamps
may enqueue a background refresh. Other queues retain their prior behavior.
Compilation status is reported by the implementation task; no new live Mongo
rollout, explain or automated test execution is implied by this phase.
