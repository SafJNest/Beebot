# Scope: AI — Training dataset

## Endpoint

`GET /api/lol/ai/training`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/ai/training'
```

The source is Mongo only. The export reads every match of the current patch
from canonical Solo/Duo, Ranked Flex and Normal Draft queues, including their
persisted queue aliases. Mongo reads the compact projection in cursor batches
of 10,000 and never reads `match_events`. The JSON body is streamed while the
cursor is read, rather than buffered in memory. Each eligible match emits its
BLUE sample followed by its RED sample. A match is omitted if either side does
not have one valid champion for each of `TOP`, `JUNGLE`, `MID`, `ADC` and
`SUPPORT`; no role is inferred from missing data.

## `200` response

```json
{
  "source": "mongo",
  "samples": [
    {
      "gameId": "EUW1_123456",
      "patch": "16.17",
      "side": "BLUE",
      "participants": [
        {"championId": 266, "role": "TOP"},
        {"championId": 64, "role": "JUNGLE"},
        {"championId": 103, "role": "MID"},
        {"championId": 222, "role": "ADC"},
        {"championId": 111, "role": "SUPPORT"}
      ]
    }
  ]
}
```

`samples` can contain fewer than two entries per stored match because
incomplete or unsupported matches are intentionally skipped.

## Owner

`AiTrainingController` → `MongoDB.forEachAiTrainingSample`.
