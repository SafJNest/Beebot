# Scope: summoner — Live game

## Endpoint

- `GET /api/lol/{shard}/livegame/{puuid}`
- `GET /api/lol/{shard}/livegame-by-name/{gameName}/{tagLine}`

La seconda rotta risolve il Riot ID in PUUID e usa lo stesso flusso spectator
della prima.

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/livegame/Qx7m2vW8-example-puuid'
curl 'http://localhost:8080/api/lol/EUW1/livegame-by-name/Player/EUW'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Descrizione |
|---|---|---|---:|---|
| `shard` | path | enum `LeagueShard` | sì | Shard Riot del summoner. |
| `puuid` | path | string | prima rotta | PUUID Riot canonico. |
| `gameName` | path | string | seconda rotta | Parte prima di `#` nel Riot ID. |
| `tagLine` | path | string | seconda rotta | Parte dopo `#` nel Riot ID. |

I segmenti path devono essere URL-encoded quando contengono caratteri riservati.

## Risposta `200`

`LiveGame` espone solo dati spectator necessari: identificativo e inizio della
partita, queue/mode/type/map, ban per team e participants. I tempi sono Unix
epoch milliseconds. Ogni participant include champion, team, spell, rune e un
`profileOverview` opzionale. Quest'ultimo esiste solo per profili già presenti
in Redis/Mongo e contiene `summoner`, `ranks`, le `masteries` e tre
`championStats`: il champion in partita più i due più giocati distinti. La
lettura passa dallo stesso punto della pagina profilo: una statistica stale
resta nella risposta e accoda il suo refresh; nessun fetch Riot viene eseguito
per gli altri participant.

In spectator mode, un participant senza PUUID resta nel roster con `championId`
e `riotId` uguale al nome del champion, oltre a `team`; tutti gli altri campi
participant sono `null`.

```json
{
  "notInGame": false,
  "gameId": 123456789,
  "startedAt": 1714521600000,
  "gameLength": 120,
  "platform": "EUW1",
  "queue": "RANKED_SOLO_5X5",
  "mode": "CLASSIC",
  "type": "MATCHED_GAME",
  "map": "SUMMONERS_RIFT",
  "bans": {"BLUE": [157, 238, 432], "RED": [266, 64, 412]},
  "participants": [{
    "puuid": "Qx7m2vW8-example-puuid",
    "riotId": "Player#EUW",
    "championId": 157,
    "icon": 29,
    "team": "BLUE",
    "summonerSpell1": 4,
    "summonerSpell2": 14,
    "runes": {
      "primaryTree": 8000,
      "keystone": 8005,
      "primaryRunes": [9111, 9104, 8014],
      "secondaryTree": 8300,
      "secondaryRunes": [8345, 8347],
      "statShards": [5008, 5008, 5010]
    },
    "profileOverview": {
      "summoner": {"summonerId": 12345678, "puuid": "Qx7m2vW8-example-puuid", "riotId": "Player#EUW", "region": "EUW1", "level": 527, "icon": 29},
      "ranks": [],
      "masteries": [{"championId": 157, "level": 7, "points": 200000}],
      "championStats": [
        {"reference": 157, "games": 42, "wins": 24, "winrate": 57.14},
        {"reference": 238, "games": 31, "wins": 18, "winrate": 58.06},
        {"reference": 64, "games": 22, "wins": 10, "winrate": 45.45}
      ]
    }
  }]
}
```

Quando il summoner esiste ma non è in una partita, la risposta resta `200`:

```json
{
  "notInGame": true,
  "gameId": null,
  "startedAt": null,
  "gameLength": null,
  "platform": null,
  "queue": null,
  "mode": null,
  "type": null,
  "map": null,
  "bans": {},
  "participants": []
}
```

## Cache e refresh

Il risultato spectator attivo è memorizzato per 60 secondi nella chiave
`SPECTATOR_CURRENT`; il passaggio a cinque minuti resta pianificato. Il caso
`notInGame` non viene memorizzato. `POST /profile/{puuid}/refresh` invalida sia
la cache Redis sia quella R4J spectator; la successiva GET livegame rifà la
richiesta a Riot.

Ogni roster spectator accoda subito una scrittura Mongo con PUUID, Riot ID,
shard e icon. Per tutti i participant viene inoltre accodata una lettura R4J
Summoner: la risposta HTTP non attende nessuna delle due operazioni e non
richiama Account API, perché il Riot ID è già contenuto nella response
spectator. Al completamento della coda il profilo Mongo viene aggiornato con
level e gli altri dati canonici del Summoner.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Shard, PUUID, game name o tag line non validi. |
| `404` | Il summoner non esiste. |

## Owner

`LolController`, `SummonerService`, `R4JQueue`, `RedisKey.SPECTATOR_CURRENT`
e `LiveGame`.
