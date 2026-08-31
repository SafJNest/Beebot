# Scope: champion — Tier list

## Endpoint

`GET /api/lol/champions/tier-list`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/champions/tier-list' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'rank=CHALLENGER' \
  --data-urlencode 'region=KR' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---:|---:|---|---|
| `patch` | query | string `major.minor` | no | current patch | Dataset patch. |
| `rank` | query | enum `TierType` | no | no filter | Minimum tier of the dataset; `CHALLENGER` contains only Challenger. |
| `region` | query | enum `LeagueShard` | no | internal `GLOBAL` aggregate | Shard to aggregate. Do not send `GLOBAL` or `UNKNOWN`. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Dataset queue. |

## `200` response

Queues with lanes always return TOP, JUNGLE, MID, BOT and UTILITY. A
lane-less queue returns a single entry with `role: null`.

```json
{
  "roles": [
    {
      "role": "UTILITY",
      "champions": [
        {
          "champion": {
            "id": 412,
            "name": "Thresh",
            "image": "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/412.png"
          },
          "eligibleForRole": true,
          "tier": "S",
          "tierScore": 1.34,
          "stats": {
            "games": 12540,
            "picks": 12540,
            "bans": 1830,
            "wins": 6646,
            "winrate": 0.53,
            "pickrate": 0.084,
            "banrate": 0.012
          },
          "counters": [
            {
              "champion": { "id": 157, "name": "Yasuo", "image": "..." },
              "games": 312,
              "wins": 145,
              "losses": 167,
              "winrate": 0.465,
              "adjustedWinrate": 0.473,
              "weightedDelta": -0.057
            }
          ],
          "strongAgainst": []
        }
      ]
    }
  ],
  "metadata": {
    "pagination": null,
    "lastUpdate": 1710000000000,
    "refresh": false,
    "filter": {
      "champion": 0,
      "lane": null,
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "rank": "CHALLENGER",
      "patch": "14.10",
      "region": "KR"
    }
  }
}
```

The tier list exposes only champions with `eligibleForRole: true`. Eligibility is
computed jointly across the role buckets of the response. For each
champion-role pair, `roleShare = picksInRole / totalPicksAcrossRoles` is computed,
then a two-group clustering uses `x = log1p(picksInRole)` and
`y = logit(roleShare)`. The cluster with the high centroid on both
dimensions identifies roles actually played; the same champion can
therefore be eligible in multiple roles. No absolute
threshold on games, picks or pick rate is applied. For lane-less queues the single bucket
does not require inter-role classification and keeps all champions with picks.
When `roleShare` is exactly `1`, the finite limit of the logit uses as
resolution the smallest positive off-role volume observed in the same dataset:
this way a few picks concentrated in a single role do not become an
infinite value and are not mistaken for a truly established role.

`tierScore` combines Z-scores of the eligible-only population: 50% adjusted
win rate, 45% pick rate and 5% ban rate. The adjusted win rate keeps the
raw `winrate` in the response, but uses the overall role win rate as a
prior: `(wins + priorStrength * roleAverageWinrate) / (picks + priorStrength)`,
where `priorStrength` is the median of eligible picks. The Z-score deviation
also includes posterior variance, so a 1/1 does not become
a tier outlier. Buckets are `S+ >= 2`, `S >= 1`, `A >= 0.25`,
`B >= -0.25`, `C >= -1` and `D < -1`.

`counters` and `strongAgainst` contain at most three matchups. Their ordering
uses `weightedDelta = adjustedMatchupWinRate - adjustedChampionWinRate`.
Before the median, both lists remove opponents that are not
`eligibleForRole` in the same role as the response.
`matchupPriorStrength` is the median of matchup games available for the
individual champion and also acts as a relative threshold: only
a matchup with `matchupGames >= matchupPriorStrength` enters the lists. The eligible
matchup is then corrected with `(matchupWins + matchupPriorStrength *
adjustedChampionWinRate) / (matchupGames + matchupPriorStrength)` before
computing the delta. There is no global matchup threshold.

## Partial state

If one or more roles are not ready or are stale, the endpoint still responds with
`200` containing only the ready roles, `metadata.refresh=true` and starts the deduplicated stats matrix
for patch and queue. The partial response is not cached.

## Storage and cache

`champion_stats` remains the only persisted source. Mongo returns a
narrow projection of overview and matchup; score, tier and counter/strong
lists are computed in Java. The complete response is cached in Redis and is
invalidated when the stats refresh replaces one of the source buckets. The
cache key is versioned together with the algorithm, so a new formula cannot
reuse payloads produced by the previous version. There is no
Mongo tier-list collection.

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service and cache: [`ChampionService`](../../../src/main/java/com/safjnest/lol/service/ChampionService.java)
- Analyzer: [`ChampionTierAnalyzer`](../../../src/main/java/com/safjnest/lol/service/ChampionTierAnalyzer.java)
- Source projection: [`MongoDB`](../../../src/main/java/com/safjnest/nosql/MongoDB.java)
- Success model: [`ChampionTierList`](../../../src/main/java/com/safjnest/lol/model/ChampionTierList.java)
