# Champion matchup: lane and item payload

## Obiettivo

Estendere il matchup champion con una lettura chiara della fase di lane e dei
primi spike di oggetti. Il payload descrive sempre il champion della statistica
contro `opponent`; non espone contatori tecnici di copertura o dati evento
grezzi.

## Prerequisito dati

Ogni match ammesso al refresh Champion Statistics deve avere il relativo
`match_events`. La garanzia va applicata a monte, quando il match viene
tracciato o reso disponibile per le statistiche: un match senza eventi non
entra nel dataset del refresh. Il job non introduce fallback per-match, rate
calcolati su campioni parziali, join alternativi o loop di recupero.

L'associazione `match` -> `match_events` deve essere uno-a-uno. Se una query a
batch non rispetta questa invariabile, il job fallisce senza marcare il filtro
come `ready`; non pubblica zeri o risultati parziali.

## Payload proposto

```json
{
  "opponent": 157,
  "lane": "TOP",
  "games": 248,
  "winrate": 0.524,
  "first15Minutes": {
    "goldAt15": {
      "championAverage": 5620,
      "opponentAverage": 5302,
      "championAheadRate": 0.571
    },
    "csAt15": {
      "championAverage": 128.4,
      "opponentAverage": 123.0,
      "championAheadRate": 0.610
    },
    "oneVsOne": {
      "championKills": 46,
      "championDeaths": 29,
      "championGotFirstKillRate": 0.148
    }
  },
  "itemTimings": {
    "firstCompletedItem": {
      "championFinishedFirstRate": 0.560,
      "championAverageFinishSeconds": 683,
      "opponentAverageFinishSeconds": 714
    },
    "firstBootUpgrade": {
      "championFinishedFirstRate": 0.530
    }
  },
  "winConditions": {
    "winrateWhenChampionAheadInGoldAt15": 0.682,
    "winrateWhenChampionBehindInGoldAt15": 0.341,
    "winrateWhenChampionGetsFirstSoloKill": 0.714
  }
}
```

Il client formatta i valori `*Seconds` come minuti e secondi; l'API conserva i
secondi per permettere medie e confronti non ambigui.

## Semantica

- `first15Minutes` usa solo eventi e snapshot con timestamp entro 15:00.
- `oneVsOne` conta soltanto le uccisioni dirette tra il champion e il suo
  avversario della stessa lane, senza assist. Kill da gank o teamfight non sono
  presentate come duelli di lane.
- `championGotFirstKillRate` significa che il champion ottiene il primo kill
  diretto della coppia nel match, non il first blood globale.
- `goldAt15` e `csAt15` riportano entrambi i valori medi, non una differenza
  con segno implicito. `championAheadRate` e la quota di match in cui il primo
  valore e strettamente maggiore del secondo.
- `firstCompletedItem` esclude consumabili, starter, trinket e componenti. Un
  item e valido solo se completo secondo i dati statici item.
- `firstBootUpgrade` considera soltanto un upgrade delle scarpe, non l'acquisto
  degli stivali base.
- La ricostruzione degli item applica in ordine `ITEM_PURCHASED`,
  `ITEM_DESTROYED` e `ITEM_UNDO`; gli undo non possono produrre uno spike.
- `winConditions` e calcolato sui match del matchup, quindi e descrittivo e
  non una previsione causale.

## Raw e complessita

Durante la fase eventi il job estrae una sola volta, per ogni giocatore, i
fatti compatti necessari: gold/CS al 15, esito del duello, timestamp del primo
item completato e del primo upgrade scarpe. Gli accumulatori raw conservano
solo conteggi, somme e numeratori; non serializzano ne riattraversano gli
eventi grezzi durante assemble, rollup rank/region o write.

Questo mantiene il pairing una volta per game e lascia invariati bucket raw,
rollup cumulativo, chiavi `Filter`, cache e ready-state. I campi BSON e JSON
gia pubblicati restano identici; il nuovo payload e un'estensione additiva del
contratto Champion Statistics e richiede, nel task di implementazione,
aggiornamento coordinato del modello, serializer, API, documentazione endpoint
e consumer.

## Ordine di implementazione

1. Rendere vincolante a monte la disponibilita uno-a-uno di `match_events`.
2. Aggiungere i fatti evento compatti ai bucket raw e fixture con kill diretti,
   assist, undo e upgrade oggetti.
3. Assemblare il payload senza modificare le metriche matchup esistenti.
4. Aggiornare contratto API e consumer, mantenendo la forma attuale della mappa
   `matchups` e le sue chiavi.
5. Confrontare BSON/JSON esistenti e verificare che l'estensione non modifichi
   i valori gia pubblicati.
