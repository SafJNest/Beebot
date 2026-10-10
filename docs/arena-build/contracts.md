# Contratto Arena semplificato

Stato: schema 4/4 e flusso interno implementati il 2026-10-10. I test focalizzati offline passano; restano aperti i gate Mongo operativi e il rollout.

## Obiettivo

Per ogni partita Arena del campione:

1. identificare il core `bootsId + firstPrismaticId`;
2. ordinare gli item leggendari acquistati dalla timeline;
3. aggregare Prismatics e augments con games, wins e win rate, per core e globalmente;
4. conservare la posizione degli augments, senza posizione per i Prismatics.

Il payload non conserva sequenze complete, rami progressivi o metriche diagnostiche non necessarie a queste aggregazioni.

## Flusso e ownership

Il percorso interno è `ComputeScheduler.startChampionArena` → `ChampionService.refreshArena` → `ChampionBuildProvider.forEachArenaBatch` → `MongoDB.forEachChampionArenaMatchBatch` → `ArenaChampionAnalyzer.Accumulator` → `MongoDB.upsertChampionArena`.

- La sorgente filtra `CHERRY` e patch completa; il catalogo item è passato come input immutabile e coerente con quella patch.
- Mongo legge al massimo 100 match per batch e fa una query `$in` su `match_events._id` per le timeline associate. I match senza timeline restano nel batch.
- Il callback elabora il batch in modo sincrono. Ogni `Match` Arena mantiene un solo `JSONObject` in `events`, senza duplicarlo in `eventData`; Mongo rilascia documenti, timeline e match al termine. L'accumulatore conserva solo contatori e chiavi aggregate.
- MongoDB possiede la persistenza. Un documento per `filterKey` resta in `champion_builds`; Arena aggiorna solo `build.arena` con un update atomico. La scrittura standard mantiene i propri percorsi.
- Non sono introdotti collection, provider, service, queue o endpoint nuovi. La risposta API standard continua a escludere `build.arena`.

Il job è opt-in: non esiste un caller di produzione che lo avvii automaticamente, né un trigger API o schedulato. Un'eventuale attivazione automatica richiede una decisione separata.

## Estrazione dei fatti

### Eventi e primo Prismatic

`ArenaGameParser` legge gli `item_events` del `match.events` già idratato dal join Arena; non ricostruisce l'inventario finale e non richiama il parser timeline della build standard. Per i chiamanti che forniscono solo il modello canonico può leggere `eventData` come fallback.

1. Accetta `ITEM_PURCHASED`, `ITEM_SOLD`, `ITEM_UNDO` e `ITEM_DESTROYED` con timestamp e ID validi. L'evento deve appartenere al participant; se è presente la mappa `participants`, questa deve confermare il PUUID. Eventi duplicati vengono scartati e gli altri sono ordinati per timestamp, poi per indice originale.
2. La lista degli acquisti contiene solo `ITEM_PURCHASED`. Un acquisto trasformato (`item` e `after` positivi e diversi) viene escluso. Un `ITEM_UNDO` annulla solo l'acquisto corrispondente immediatamente precedente nell'ordine degli eventi; vendite e distruzioni non rimuovono acquisti già osservati.
3. Si esclude un acquisto se `item`, `before` o `after` coinvolge l'Anvil `220007`. Il primo Prismatic classificato nella timeline è `firstPrismaticId`.
4. Se la timeline non fornisce un Prismatic, gli slot finali `item0..item5` sono esaminati in ordine e il primo Prismatic noto diventa il fallback. Se manca anche questo, il participant-game non entra in un core ma continua a contribuire alle opzioni globali disponibili.

Il timestamp della timeline serve a ordinare gli acquisti. Non viene persistito nel payload Arena.

### Boots e core

I boots sono scelti con questa precedenza: `participant.boots` se classificato come boots; altrimenti l'unico boots riconosciuto negli slot finali; altrimenti l'unico boots riconosciuto negli acquisti della timeline. Se una fonte contiene più boots candidati e le precedenti non risolvono il valore, il core resta mancante.

La chiave del core è sempre `bootsId + firstPrismaticId`. Un participant-game entra nelle statistiche del core solo se entrambi gli ID sono disponibili. Un core mancante non esclude il participant-game dalle statistiche e dalle opzioni globali.

### Build leggendaria

La build usa gli acquisti timeline classificati `Kind.ITEM` dal catalogo patch-aware: item completi non-Prismatic, con una sola occorrenza di ciascun ID per participant-game, nell'ordine del primo acquisto valido.

Le opzioni sono aggregate per posizione ordinale degli item leggendari (`1`, `2`, `3` ...), non per timestamp. Gli slot finali non ricostruiscono l'ordine. Una timeline assente o senza acquisti leggendari non produce item ordinati; una partita parziale contribuisce solo con gli acquisti validi disponibili.

### Prismatics

Gli ID Prismatic derivano dagli acquisti timeline e dagli slot finali, senza posizione. L'Anvil `220007` è escluso. Ogni ID contribuisce una volta per participant-game. Il totale globale include il primo Prismatic; la lista del core lo esclude perché è già nella chiave.

### Augments

Gli augments provengono da `participant.augments`. La posizione è l'indice originale, a partire da 1; ID nulli o non positivi sono omessi senza rinumerare gli elementi successivi. Si aggrega la coppia `augmentId + position`, sia per core sia globalmente.

### Esito e statistiche

Un participant-game è una vittoria se `participant.win` è vero oppure `subTeamPlacement >= 3`. Ogni aggregato espone `games`, `wins` e `winRate = wins / games`, senza soglie né correzioni.

- `stats.games` conta participant-games; `coverage.matches` conta i match Mongo accettati. La sorgente restituisce un solo documento per `_id`; l'accumulatore mantiene un controllo duplicati compatto con ID numerici primitivi, non con una `String` per partita.
- `coreGames` e `missingCoreGames` sono participant-games con core completo o incompleto.
- `firstPrismaticFallbackGames` conta i fallback dagli slot finali.
- `buildTimelineGames` conta i participant-games con almeno un item leggendario ordinabile, non tutte le timeline presenti.
- Core ordinati per boots e primo Prismatic; item per posizione e ID; Prismatics per win rate decrescente, games decrescenti e ID; augments per posizione, poi win rate, games e ID.

## Versione e rollout

La forma 4/4 è implementata e non è compatibile con il payload 3/3. Non esistono reader o conversioni per lo schema precedente; i dati Arena dovranno essere rigenerati prima dell'uso.

I test focalizzati verificano parser, aggregatore, callback del job, join/persistenza con fixture e round-trip BSON locale. Restano da completare un round-trip sul database di test, la misura del documento combinato e la verifica operativa delle scritture Arena/standard. Non effettuare rebuild o rollout sul database reale come parte della verifica offline.
