# Arena builds

Il contratto attivo è CHERRY: l’API espone direttamente `build` con core boots+primo Prismatico, build complete, sei posizioni augment e statistiche Prismatici. Non calcola né espone skill order per Arena. `builds[].items` contiene gli acquisti attribuiti nella timeline, escludendo boots, Prismatici, Juice e Anvil `220007`; usa gli slot finali solo come fallback se la timeline item manca.

## Riferimenti

- [Contratto](contracts.md): sorgenti, regole di parsing, statistiche e persistenza.
- [Schema HTTP](champion-schema.json): forma canonica della risposta CHERRY.

## Flusso e limiti

- Il flusso usa il join raw condiviso, legge match in batch e aggiorna solo `build.arena` con `arenaLastUpdate`.
- I payload CHERRY già salvati con `skillOrders` restano leggibili; il campo viene ignorato e non viene restituito. La sua assenza non avvia un refresh. I vecchi campi `consumables` e i nuovi contatori stats mantengono la compatibilità esistente.
- Il comando Discord `/champion` non mostra il campo “Skill Order” per la coda CHERRY; le build normali mantengono la visualizzazione.
- Il placement e i contatori Arena sono esposti sotto `stats`; l’aggiunta di `subteamsById` e `overview.placements` non cambia le sinergie.
- Restano da verificare round-trip BSON, sizing rappresentativo, explain degli indici e comportamento operativo su ambiente di test.
