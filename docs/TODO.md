# Discord LoL summoner resolution (done)

Discord LoL commands resolve identity through the bot Mongo/canonical path:

- linked user / empty args → `UserData` cached canonical `Summoner` (ordered by Mongo `_id`);
- `name#tag` → channel shard → `getPuuidByRiotId` then `SummonerService.get` (Mongo first, Riot on miss + persist);
- `UserData.riotAccounts` holds `Map<String, Summoner>` (canonical), not `puuid → region`;
- embeds/buttons presentation unchanged.

Tracker live polling may still use `getRiotSummoner`.
