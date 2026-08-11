# Macro-task 0001: canonical summoner model

## Objective

Create and adopt the canonical summoner model shared by profile, search and leaderboard.

## Dependencies

- Macro-task 0000 approved;
- ADR-0001 and ADR-0002 accepted.

## Scope

- introduce `Summoner`, `Rank`, `Mastery`, `SummonerView` and overview models under `lol.model.summoner`;
- move or adapt rank, champion and mastery data;
- update `LeagueService` factories and consumers;
- reuse existing champion, lane, queue and summoner-image utilities;
- remove `SummonerRank`, `ProfileMastery` and duplicate summoner records.

## Out of scope

- full match consolidation;
- DatabaseTracker asynchronous refresh ownership;
- final leaderboard cache rewrite.

## Invariants

- `riotId` remains a `String`;
- `Rank` contains queue, tier, LP, wins, losses and derived games/winrate data;
- `Mastery` is the only mastery name;
- `SummonerView` is the complete profile projection;
- no mapper owns domain construction.

## Acceptance criteria

- profile/search data can be built from canonical models;
- no second owner exists for summoner, rank or mastery;
- existing consumers compile or are explicitly migrated;
- no new local Riot ID parser or champion lookup is introduced.

## Handoff

Report model files, factory decisions, migrated consumers, verification and remaining old-name references.
