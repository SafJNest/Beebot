# Piano di implementazione Arena

Stato aggiornato il 2026-10-10. Parser, aggregatore, payload 4/4 e streaming a batch sono implementati. I 29 test Arena focalizzati passano; round-trip BSON sul database, sizing rappresentativo e rollout restano aperti.

## Fase 0 — Contratto: completata

- Usare [contracts.md](contracts.md) come requisito attivo.
- Usare [schema.md](schema.md) come schema target.
- Conservare la persistenza condivisa in champion_builds/build.arena e l'ownership di provider, service e scheduler.
- Non ampliare il lavoro a un endpoint o a una presentazione pubblica.

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

- round-trip e scrittura del sottoalbero 4/4 su MongoDB di test;
- sizing BSON rappresentativo e verifica della headroom del documento combinato;
- explain degli indici e misure di heap sul volume previsto;
- verifica in ambiente test delle scritture standard e Arena in entrambi gli ordini;
- definizione esplicita del trigger: oggi `startChampionArena` non ha un caller di produzione.

Non eseguire query, rebuild, backfill o scritture sul database reale come parte della verifica offline.

## Classi e ownership

Prima della semplificazione la cartella Arena conteneva nove classi di dominio/aggregazione: ArenaGameParser, ParsedArenaGame, ArenaItemCatalog, FirstPrismaticResolver, FirstPrismaticResult, FirstPrismaticResolveType, PrismaticItemClassifier, ArenaChampionAnalyzer e ArenaBuildData.

Risultato: cinque classi. Sono conservate ArenaGameParser, ParsedArenaGame, ArenaItemCatalog, ArenaChampionAnalyzer e ArenaBuildData. Il selettore del primo Prismatico è nel parser e la classificazione patch-aware nel catalogo; FirstPrismaticResolver, FirstPrismaticResult, FirstPrismaticResolveType e PrismaticItemClassifier sono stati rimossi. Le classi shared di caricamento, persistenza e job restano ai rispettivi proprietari.

Il job è opt-in e non ha un caller di produzione, trigger API o refresh schedulato. La verifica funzionale del percorso da job a writer resta distinta dal collegamento a un trigger.
