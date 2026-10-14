# Arena builds

Il contratto attivo è CHERRY: l’API espone direttamente `build` con core boots+primo Prismatico, build complete, sei posizioni augment e statistiche Prismatici. `builds[].items` include item finali e consumabili riconosciuti, incluso l’Anvil `220007`; gli item Prismatici restano esclusi.

## Documenti attivi

- [Contratto](contracts.md): sorgenti, regole di parsing, statistiche e ownership.
- [Schema](schema.md): struttura HTTP e semantica dei dati.
- [Fasi](phases.md): cronologia dell’implementazione e gate operativi.

## Flusso e limiti

- Il flusso usa il join raw condiviso, legge match in batch e aggiorna solo `build.arena` con `arenaLastUpdate`.
- I payload CHERRY presenti vengono restituiti anche se hanno il vecchio campo `consumables` separato o non contengono ancora alcuni nuovi contatori stats; un refresh per dati stale viene accodato in background.
- Il placement e i contatori Arena sono esposti sotto `stats`; l’aggiunta di `subteamsById` e `overview.placements` non cambia le sinergie.
- Prima del rollout restano da completare round-trip BSON su MongoDB di test, sizing rappresentativo, explain degli indici e verifica operativa.
