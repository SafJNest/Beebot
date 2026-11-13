# Beebot guild settings

Base URL: `https://api.safjnest.com/api/beebot`. Both endpoints accept application `X-API-Key` plus `Authorization: Bearer <sessionToken>`, or a personal `X-API-Key` alone, through the existing [authentication flow](beebot-auth.md). Each request checks the requested guild ID against the per-session authorized list shared by `/me`, `/guilds` and the `/servers` alias. Owners and members with Administrator or Manage Server permission are allowed. The list has a 60-second Redis/in-memory cache and single-flight loading; valid cache hits do not contact Discord. The bot must be present. Responses use `Cache-Control: no-store`.

## GET /guilds/{guildId}

Returns `{ id, name, iconUrl, channels, roles, settings }`.

- `channels`: all guild channels known to JDA, including `CATEGORY` entries. Each has string `id`, `name`, `type` (JDA channel type name), nullable string `parentId` (category ID), and nullable numeric `position` (Discord's raw channel position). Categories and uncategorized channels have a null `parentId`; `position` is null for channel types without a position.
- `roles`: guild roles with string `id`, `name`, numeric `color`, numeric `position`, boolean `managed` and boolean `assignable`. `assignable` requires the bot's Manage Roles permission, a role below its highest role, and a role that is neither managed nor `@everyone`. It reflects the current Discord state and may change before use.
- `settings.general`: `prefix`, `experienceEnabled`, `voice`, derived read-only `language`, and `leagueShard` as its enum name, for example `EUW1`.
- `settings.blacklist`: `enabled`, `threshold`, nullable `channelId`.
- `settings.channels`: persisted channel overrides indexed by channel ID, with `experienceEnabled`, `experienceModifier`, `statisticsEnabled`, and nullable `leagueShard`. Channels without a row use the bot defaults: experience and statistics enabled, modifier 1, inherited guild shard.
- `settings.welcome`, `leave`, `boost`, `levelUp`: alert state described below.
- `settings.twitch`: alert states indexed by numeric streamer ID, with `streamerId` and nullable `roleId`.
- `settings.reward`: alert states indexed by level, with `level` and `temporary`.
- `settings.warning.actions`: existing automated warning actions, read-only. Uses the existing action model (`id`, `action`, `roleId`, `actionTime`, `infractions`, `infractionsTime`, and derived `actionMessage`). Durations are seconds; action codes retain the bot's values (1 mute, 2 kick, 3 ban).

GET reads SQL and the existing Discord cache without creating guild rows, alerts, roles or channels. If a guild has no stored configuration, it returns the bot's defaults, missing single alerts and empty collections. SQL failures return an unavailable error, not empty configuration.

### Alert state

All alert states include `id`, `exists`, `enabled`, `valid`, `message`, `privateMessage`, `channelId`, `sendType`, `roleIds` and `problems`. Twitch/reward fields (`streamerId`, `roleId`, `level`, `temporary`) are null for other types.

```json
{
  "exists": true,
  "enabled": true,
  "valid": false,
  "message": "Ciao!",
  "privateMessage": "",
  "channelId": null,
  "sendType": "CHANNEL",
  "roleIds": [],
  "problems": [
    { "code": "missing_channel", "field": "channelId", "message": "Manca il canale" }
  ]
}
```

`exists` indicates a stored alert. `enabled` is its saved switch. `valid` follows the existing `AlertData.isValid(guild)` rules, with reasons computed in the alert domain:

- `missing_message`: neither public nor private message exists.
- `missing_channel`: channel is null, except for PRIVATE delivery, level-up and reward.
- `disabled`: the alert switch is off.
- `experience_disabled`: a level-up alert belongs to a guild with experience disabled.

A missing alert has `exists`, `enabled` and `valid` false, null configuration, empty `roleIds` and empty `problems`. Missing Twitch/reward entries are omitted from GET collections. An unsuccessful POST for a missing entry returns its missing alert state.

Validity preserves the current bot rules, including message fallback and the existing null/nonempty checks; it does not change Discord commands or their output. In particular, reward role presence is not currently part of `isValid()`, even though the Discord configuration view mentions it. `valid` does not promise delivery: deleted channels, Discord permissions, closed DMs, subscriptions, and additional event-handler conditions can still prevent execution. These APIs do not redesign or repair those handlers.

## POST /guilds/{guildId}/settings

Body is a non-empty JSON object. Each top-level key selects a block; fields omitted within that block stay unchanged. Existing items are updated; missing items are created with the same configurable incomplete-alert defaults as the bot (empty messages, null channel, enabled, CHANNEL delivery). Sending `{}` for a single alert creates it if missing; an empty collection changes nothing.

```json
{
  "leave": {
    "message": "ciao",
    "privateMessage": "ciao2",
    "channelId": "1188895746649493645"
  },
  "twitch": {
    "123456": { "message": "Sono live!", "roleId": null }
  },
  "reward": {
    "5": { "roleIds": ["1188895746649493646"], "temporary": true }
  }
}
```

Writable fields:

| Block | Fields |
| --- | --- |
| `general` | `prefix` (1–32 characters), `experienceEnabled` (boolean), `voice` (existing TTS voice name), `leagueShard` (enum name) |
| `blacklist` | `enabled` (boolean), `threshold` (integer 0–127), `channelId` (text channel ID or null) |
| `channels.{channelId}` | `experienceEnabled`, `statisticsEnabled` (booleans), `experienceModifier` (finite nonnegative number), `leagueShard` (enum name or null to inherit) |
| `welcome`, `boost` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType`, `roleIds` |
| `leave` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType` |
| `levelUp` | `message`, `privateMessage`, `enabled`, `sendType`, `roleIds` |
| `twitch.{streamerId}` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType`, `roleId` |
| `reward.{level}` | `message`, `privateMessage`, `enabled`, `sendType`, `roleIds`, `temporary` |

Message strings have a maximum of 2000 characters. Empty messages are allowed. Null clears `message` to `""`, `privateMessage` to SQL null, and nullable channel/role references to SQL null. Other fields reject null. `sendType` is `CHANNEL`, `PRIVATE` or `BOTH`. `roleIds` is an array of up to 25 role IDs: when present it replaces that alert's roles; `[]` removes them. Role IDs are deduplicated. Channels and roles must belong to the guild; fixed alert/blacklist channels must be text channels. IDs are JSON strings. Twitch streamer IDs are numeric strings up to 32 digits; reward keys are canonical integer strings 1–100. Streamer identity is supplied by ID, not resolved by name. Level-up/reward have no fixed channel. Channel shard ordinal zero is the existing inheritance sentinel and must be represented by null.

Unknown fields, read-only fields, unknown blocks, bad references and incorrect types are reported against the relevant block/item. The `warning` block is read-only; its future write endpoint is outside this change. Arrays cannot replace keyed collections. Omitted collection entries remain unchanged. This endpoint does not delete alerts or collection entries; alerts can be disabled.

### Results and independent transactions

The response uses HTTP 200 for a parsed request with per-block results, including requests where all blocks fail. Each requested block has `{ status, errors, alert }`; `alert` is null for non-alert blocks. Collections contain the same results under each requested key.

```json
{
  "leave": {
    "status": "ok",
    "errors": [],
    "alert": {
      "id": 123,
      "exists": true,
      "enabled": true,
      "valid": false,
      "message": "ciao",
      "privateMessage": "",
      "channelId": null,
      "sendType": "CHANNEL",
      "roleIds": [],
      "streamerId": null,
      "roleId": null,
      "level": null,
      "temporary": null,
      "problems": [
        { "code": "missing_channel", "field": "channelId", "message": "Manca il canale" }
      ]
    }
  },
  "blacklist": {
    "status": "error",
    "errors": [{ "field": "threshold", "code": "invalid_threshold", "message": "Deve essere un intero tra 0 e 127" }],
    "alert": null
  }
}
```

Validation errors prevent the whole block/item from saving. Each valid block/item is committed in its own SQL transaction, including alert roles and Twitch/reward rows; SQL failure rolls back that whole unit. Other blocks/items continue. Updates change only supplied columns and serialize API writes using the guild row lock. The local `GuildCache` entry is invalidated only after a successful commit; the bot reloads it on next use. This invalidation is local to the running bot instance.

An alert can save successfully with `valid: false`: operational `problems` are distinct from request `errors`. Returned alert states are refreshed after processing the complete request so that changes to guild experience are reflected regardless of key order. Rejected updates return the currently stored alert state.

Twitch registration runs after commit and is retried on subsequent updates to that streamer. If it fails, status is `saved_with_errors`, the committed alert is returned and `errors` contains `twitch_subscription_failed`. Do not interpret this status as rollback. External registration and SQL cannot share a transaction.

Malformed JSON, missing body, non-object/empty root or invalid guild ID returns 400. Invalid credentials return 401, missing owner/Administrator/Manage Server access 403, absent bot 404, and unavailable SQL 503. Discord-backed authorization retains the existing authentication error statuses. Request-level errors use the authentication `{ code, message }` shape (plus `retryAfter` and a `Retry-After` header on Discord 429). There is no request-wide transaction: a dependency outage after earlier commits does not undo those commits.

## Storage and compatibility

Uses existing guild, channel, alert, alert_role, alert_twitch, alert_reward and automated_action tables via `GuildSettingsDB`, with prepared statements and explicit transactions. No new migration or database is introduced. The database must already support the columns used by current bot code (`alert.private_message`, `alert.send_type`, `channel.league_shard`); some historical SQL dumps in this repository omit them. No schema changes are executed automatically.

LoL controllers, models, Mongo collections, Redis caches, command registration and Discord presentation are unchanged. No shard fan-out or queued progress reporting is required for these guild configuration operations.
