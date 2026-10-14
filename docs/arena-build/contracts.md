# Contratto Arena CHERRY

Stato: nuovo contratto HTTP schema approvato, approvato il 2026-10-14. Forma completa in [schema.md](schema.md) e [champion-schema.json](champion-schema.json).

## Flusso e ownership

Spring → ChampionService → ComputeScheduler → matrix/build-only → ArenaChampionAnalyzer → MongoDB.upsertChampionArena. Tutti i consumer usano `MongoDB.forEachChampionRawMatchEventBatch`: unico join raw, batch 10.000 per matrix e 100 per build-only, con match senza eventi consegnati ai consumer. Stats e build standard mantengono la propria eligibilità timeline. Il catalogo item è immutabile e selezionato dalla patch richiesta.

La sorgente applica queue, patchMajor, champion, rank e region canonici; per patch complete CHERRY applica anche patch esatta. Role è invalido. La proiezione condivisa include `participants.subTeam` e `participants.subTeamPlacement` per le statistiche, oltre a ID, ai campi build e `participants.skillOrder`. Non si aggiungono query per sinergia o full build.

Mongo è l’unico writer; QueueHandler è l’unico ingresso dei job. CHERRY API ed explicit Arena job mantengono `champion-arena:<Filter.toKey()>`. Stats/build cold condividono una scansione; un build-only miss non riscrive stats correnti. La freschezza delle stats rimane basata su ready/updatedAt.

## Estrazione

Il core è boots + primo Prismatic. Boots: campo esplicito, unico candidato finale, unico candidato timeline. Primo Prismatic: primo acquisto attribuito valido, altrimenti primo slot finale riconosciuto. Un core mancante conserva esiti e opzioni globali.

Gli item events attribuiti sono ordinati per timestamp e indice, deduplicati e controllati contro la mappa participant/PUUID. `builds[].items` conserva in ordine timeline gli acquisti non annullati, una volta per ID, escludendo boots, Prismatici, Juice e Anvil `220007`. In assenza di eventi item attribuibili usa gli slot finali come fallback, con le stesse esclusioni e mantenendo eventuali ID duplicati. I timestamp non sono persistiti.

Prismatics: ogni ID osservato contribuisce una volta per participant-game, sia globalmente sia nel core, incluso il primo; rimangono separati dalle build complete. Non esiste un aggregato globale separato dei consumabili. Augments: sei posizioni originali, null/nonpositivi omessi senza rinumerazione. `skillOrders` usa gli eventi skill/level della timeline quando disponibili, altrimenti `Participant.skillOrder`, e include i tempi medi attribuibili. Riusa il ranking standard, conteggia i prefissi compatibili e calcola `pickrate` sul totale dei participant-games. Core/full build/opzioni ordinate per WR, numerosità e ID senza soglie minime.

L’esito della build conserva `participant.win || subTeamPlacement >= 3`. Stats generiche conservano l’esito raw. I contatori non vengono normalizzati o reinterpretati in questo cambio di contratto.

## Stats e HTTP

ChampionAnalyzer accumula sinergie esatte per lo stesso `subTeam`, dimensioni 2/3 e ID Arena 1–8, senza ruoli o team BLUE/RED. Aggiunge in modo indipendente la distribuzione `overview.placements` e la media pesata `overview.averagePlacement` usando `subTeamPlacement`; queste statistiche non cambiano le sinergie. I bucket rank/region mantengono la selezione canonica. I contatori sono raw e non ricevono il ranking lane-based.

Il controller esistente e i parametri rimangono invariati. Il mapper HTTP espone `build` direttamente e solo i campi stats dello schema CHERRY. Altre queue usano la serializzazione esistente. Non si introducono DTO di successo duplicati né si modifica la presentazione Discord.

## Persistenza e rilascio

`build.arena` + `arenaLastUpdate` vengono aggiornati atomicamente; i writer standard preservano Arena. Il reader CHERRY e le cache usano presenza dei dati e timestamp, senza contatore di versione. Una build priva di `skillOrders` resta valida e la sua sola assenza non avvia un refresh; i nuovi aggregati includono il campo, anche come array vuoto. I payload esistenti lo riceveranno alla prossima normale rigenerazione. La presenza dei vecchi campi `consumables` o l’assenza di alcuni nuovi contatori stats non rende il dato un miss. Gli scope stats usano il marker `ready`; gli timestamp determinano se accodare un refresh in background. Ready-empty rimane pronto.

Prima del rilascio operativo restano da verificare indici/explain, dimensione BSON combinata, memoria su dati rappresentativi e HTTP dopo riavvio. Questa modifica non riavvia il server e non rigenera il database reale. Le verifiche eseguite sono riportate nel riepilogo della task.
