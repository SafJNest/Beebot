# Schema Arena semplificato

Stato: schema 4/4 implementato nel payload interno `ArenaBuildData`. Sostituisce lo schema Arena 3/3 documentato in precedenza.

ArenaBuildData rimane il payload interno di Build e viene persistito in champion_builds sotto build.arena. Lo standard Build continua a mantenere il proprio schema e la propria scrittura.

## Forma

La radice contiene solo:

- schemaVersion e aggregationVersion;
- stats aggregate per tutti i participant-games del campione;
- cores, indicizzati dalla coppia bootsId + firstPrismaticId;
- prismatics e augments globali;
- coverage minima.

Ogni core contiene stats, items ordinati per posizione leggendaria, Prismatics raccomandati e augments per posizione. Nessun ramo progressivo o build completa combinatoria.

## Esempio

    {
      "schemaVersion": 4,
      "aggregationVersion": 4,
      "stats": {
        "games": 100,
        "wins": 52,
        "winRate": 0.52
      },
      "cores": [
        {
          "bootsId": 3006,
          "firstPrismaticId": 447001,
          "stats": {
            "games": 20,
            "wins": 12,
            "winRate": 0.6
          },
          "items": [
            {
              "position": 1,
              "itemId": 3071,
              "games": 14,
              "wins": 9,
              "winRate": 0.6429
            },
            {
              "position": 2,
              "itemId": 6610,
              "games": 8,
              "wins": 5,
              "winRate": 0.625
            }
          ],
          "prismatics": [
            {
              "prismaticId": 447012,
              "games": 7,
              "wins": 5,
              "winRate": 0.7143
            }
          ],
          "augments": [
            {
              "augmentId": 11,
              "position": 1,
              "games": 16,
              "wins": 10,
              "winRate": 0.625
            },
            {
              "augmentId": 22,
              "position": 2,
              "games": 9,
              "wins": 6,
              "winRate": 0.6667
            }
          ]
        }
      ],
      "prismatics": [
        {
          "prismaticId": 447001,
          "games": 54,
          "wins": 29,
          "winRate": 0.537
        },
        {
          "prismaticId": 447012,
          "games": 24,
          "wins": 15,
          "winRate": 0.625
        }
      ],
      "augments": [
        {
          "augmentId": 11,
          "position": 1,
          "games": 70,
          "wins": 39,
          "winRate": 0.5571
        },
        {
          "augmentId": 22,
          "position": 2,
          "games": 40,
          "wins": 24,
          "winRate": 0.6
        }
      ],
      "coverage": {
        "matches": 100,
        "coreGames": 95,
        "missingCoreGames": 5,
        "firstPrismaticFallbackGames": 5,
        "buildTimelineGames": 80
      }
    }

## Regole dei campi

- `stats.games` conta participant-games. `coverage.matches` conta i match Mongo accettati; la query restituisce un solo documento per `_id` e il contatore non mantiene la lista degli ID.
- Ogni opzione ha il proprio games e wins. winRate è wins/games; nessun tasso aggiustato e nessuna soglia minima.
- Gli item del core sono item completi non-Prismatic acquistati e ordinati dalla timeline; la posizione è l'ordine tra gli ID leggendari distinti osservati. Gli slot finali non creano l'ordine della build.
- L'elenco Prismatic globale include il primo Prismatic. L'elenco del core esclude firstPrismaticId perché è già la chiave.
- Gli augments sono separati per posizione; le posizioni mancanti rimangono mancanti.
- Nessun Prismatic contiene position.
- Il timestamp degli eventi è usato per ordinarli, ma non è persistito. Non sono persistiti placement, istogrammi, membership non posizionale, quality map, reason code o statistiche di ambiguità.
- Gli ID dell'anvil 220007 non appaiono in alcuna lista o chiave.

## Scrittura e compatibilità

Lo scrittore Arena aggiorna il sottoalbero build.arena in un solo update atomico e non sovrascrive i campi standard fratelli. La scrittura standard non sovrascrive Arena. Non usare sostituzione del documento o read-merge-replace.

La forma 4/4 non supporta il payload 3/3. I dati Arena vanno rigenerati prima dell'uso; non si legge né si converte il vecchio sottoalbero. Prima del rollout verificare round-trip BSON, limite dimensione e ordine di entrambe le scritture nel database di test.
