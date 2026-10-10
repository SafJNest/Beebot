# Arena builds

Il payload interno è schema/aggregation 4/4. Il flusso legge i match Arena con un left join delle timeline in batch da massimo 100, aggrega gli item acquistati e aggiorna solo `build.arena`. I 29 test focalizzati offline passano; la verifica su MongoDB e il rollout sono ancora aperti.

## Documenti attivi

- [Contratto](contracts.md): sorgenti, regole di parsing, statistiche e ownership.
- [Schema](schema.md): struttura persistita ed esempio completo.
- [Fasi](phases.md): stato dell'implementazione, verifica e gate operativi.

## Stato e limiti

- Il timestamp timeline ordina gli acquisti Arena, ma non viene salvato nel payload. Il Build standard conserva i suoi aggregati `averagePurchaseTimeSeconds` e `timedMatches`.
- Il job è opt-in: il percorso scheduler/service/provider/writer esiste, ma non ha un caller di produzione né un trigger API o schedulato.
- Arena resta interno; l'API standard continua a proiettare soltanto il Build generico.
- I documenti [analisi Fase 3](phase3-analysis.md) e [sizing Fase 3](phase3-sizing.md) descrivono il modello precedente e non stimano il payload 4/4.

Prima del rollout restano da completare round-trip BSON sul database di test, sizing rappresentativo del documento combinato, explain degli indici e attivazione esplicita del job.
