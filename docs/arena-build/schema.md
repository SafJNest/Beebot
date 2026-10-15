# Contratto CHERRY

Il contratto HTTP canonico è [champion-schema.json](champion-schema.json), derivato dallo schema proposto dall’utente. Vale esclusivamente per `queue=CHERRY`. Le altre queue mantengono il contratto standard.

```text
ChampionView
├── champion
├── stats
│   ├── overview
│   │   ├── averagePlacement
│   │   └── placements { "1".."8": participant-games }
│   ├── powerCurve
│   ├── trend
│   ├── synergies[] { allyChampions, matches, wins, winrate }
│   ├── subteams { "2": { games, wins, winrate }, "3": { games, wins, winrate } }
│   └── subteamsById { "1".."8": { games, wins, winrate } }
├── build
│   ├── games, wins, winrate
│   ├── coreBuilds[]
│   │   ├── id, items, matches, wins, winrate, pickrate
│   │   ├── builds[] { items, matches, wins, winrate }
│   │   ├── augments[position][rank] { id, matches, wins, winrate }
│   │   └── prismatics[rank] { id, matches, wins, winrate }
│   ├── augments[position][rank]
│   ├── prismatics[rank]
│   └── coverage
└── metadata
```

## Semantica

- L’HTTP CHERRY non contiene `build.arena`, gli altri campi standard legacy, `skillOrders`, `spell1`, `spell2`, `stats.laneStats`, `stats.matchups` o `stats.laneSynergies`. `spells` non fa parte dello schema approvato.
- `ArenaBuildData` è il modello canonico della build Arena. Il `Build` interno mantiene le proiezioni compatibili per i chiamanti esistenti; solo la serializzazione HTTP CHERRY espone direttamente `ArenaBuildData`.
- Un core contiene esattamente `[bootsId, firstPrismaticId]`; `id` usa gli stessi due ID separati da `-`. `pickrate` usa come denominatore tutti i participant-games del campione, inclusi quelli senza core.
- `builds[].items` contiene gli acquisti attribuiti nella timeline, nell’ordine d’acquisto e senza ripetere lo stesso ID; esclude boots, Prismatici, Juice e l’Anvil `220007`. Se non ci sono eventi item attribuibili, usa gli slot finali come fallback, con le stesse esclusioni e conservando gli ID duplicati.
- Entrambe le liste `augments` hanno esattamente sei contenitori. L’indice pubblico è zero-based; posizioni mancanti hanno liste vuote, senza rinumerazione. Il limite di sei si applica anche al parser.
- Prismatics globali e per core includono il primo Prismatic e tutti gli altri ID osservati, una volta per participant-game. Non hanno posizione temporale e restano separati dalle build complete. Non esiste un aggregato globale separato dei consumabili.
- Opzioni, core e full build sono ordinati per WR decrescente, poi numerosità decrescente e ID canonico; non viene applicata una soglia di campionamento.
- `synergies` usa la combinazione esatta degli alleati con lo stesso `Participant.subTeam`, con ID dei campioni ordinati. Un trio produce una coppia di alleati, non due righe indipendenti. Non si raggruppa per BLUE/RED o ruolo. Squadre senza identificatore o di dimensione diversa da 2/3 non producono relazioni.
- `subteams.2` e `.3` contano participant-games per dimensione della propria squadra; entrambi i campi sono sempre presenti. `subteamsById` conta gli stessi esiti per ID Arena e include sempre gli ID da 1 a 8: Poros, Minions, Scuttles, Krugs, Raptors, Sentinels, Wolves e Gromps.
- `overview.placements` conta i participant-games del campione per `subTeamPlacement`; contiene solo le posizioni con conteggio positivo. `overview.averagePlacement` è la media pesata di questi conteggi e vale `null` quando non è disponibile nessun placement valido.
- `synergies` continua a raggruppare gli alleati tramite `Participant.subTeam`; l’aggiunta di `subteamsById` non cambia le righe o i conteggi delle sinergie.
- Stats mantengono l’eligibilità timeline esistente e le metriche overview/powerCurve/trend generiche. Placement e distribuzione vengono dalla stessa scansione raw; i contatori degli esiti usano i valori raw già adottati dalle stats e l’esito build conserva la regola Arena preesistente. Metriche e averagePlacement indisponibili rimangono `null`, incluse quelle nel risultato vuoto; rank/region opzionali continuano ad accettare gli enum canonici.

## Coverage

`matches` conta match raw accettati; `coreGames` e `missingCoreGames` contano participant-games. `firstPrismaticFallbackGames` conta i fallback dagli slot finali. `buildTimelineGames` conta i participant-games con almeno un evento item attribuito nella timeline.

## Persistenza e migrazione

Mongo mantiene la proprietà separata: `champion_builds.build.arena` contiene il payload Arena e `arenaLastUpdate` il suo timestamp. L’update atomico non modifica gli altri campi standard. Non si sostituisce il documento di build e non si introduce una collection nuova.

`champion_stats` mantiene uno scope per queue/rank/patch/region e le foglie canoniche. Solo CHERRY aggiunge i contatori raw `arenaSynergies`, `arenaSubteams`, `arenaSubteamIds` e `arenaPlacements` nella foglia `NONE`; gli altri scope non li serializzano quando vuoti. I reader e la scansione matrix non verificano la presenza di tutti i bucket per ogni champion: un documento pronto e fresco resta utilizzabile anche se alcuni contatori Arena non sono presenti.

Reader e cache CHERRY non hanno un contatore di versione. I payload Arena già salvati vengono restituiti senza il campo `skillOrders`; il campo legacy è ignorato e la sua assenza non accoda un refresh. La scadenza può accodare un refresh in background. I vecchi campi `consumables` globali o dentro le full build sono accettati in lettura e omessi dall’output, senza causare da soli una rigenerazione. Un payload non deserializzabile o privo di `build.arena` rimane un miss. Non è previsto un rebuild del database reale in questa modifica.
