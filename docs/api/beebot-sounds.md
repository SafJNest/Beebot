# Sounds, analytics and personal greet settings

Base path: `/api/beebot`. Authentication is the existing application key plus user session, or personal key alone. Responses use `Cache-Control: no-store`. IDs are strings. See [authentication](beebot-auth.md), [request/response examples](beebot-sounds-examples.json), and [OpenAPI](beebot-sounds.openapi.json).

## Canonical Sound

All detail, list and greet responses contain the existing `com.safjnest.model.sound.Sound`. Response copies carry viewer-specific data; cached objects never carry a viewer identity. JSON fields are explicit, so paths, audio tracks, padded empty tags and operational getters are not exposed.

```json
{
  "id": "42",
  "name": "airhorn",
  "guildId": "123",
  "userId": "456",
  "uploader": { "id": "456", "name": "Luna", "avatarUrl": null },
  "public": true,
  "createdAt": "2026-03-02T10:00:00Z",
  "url": "https://cdn.example.com/sounds/42.mp3",
  "durationMs": null,
  "tags": [{ "id": "1", "name": "meme" }],
  "plays": 2140,
  "listeners": 728,
  "likes": 214,
  "dislikes": 13,
  "myPlays": 29,
  "likedByMe": true,
  "dislikedByMe": false,
  "ownedByMe": false,
  "canEdit": false,
  "canDelete": false,
  "soundboardCount": 2
}
```

Counts in Sound are global/all time. `listeners` counts distinct users associated with recorded plays, not everyone present in the voice channel. `myPlays` counts the caller's recorded plays. Uploader display data comes from the Discord cache; unavailable names are `Unknown`, avatars null. `durationMs` is currently null unless already known; no file decoding is performed during JSON serialization. `createdAt` can be null for legacy records.

`CDN_URL` is the Cloudflare origin, configured in `spring.properties` or the Spring environment. The Spring bootstrap explicitly forwards this property to the controllers; the file value takes precedence when present, otherwise the environment value is preserved. It is loaded at application startup, so changing it requires a restart. The object key is `sounds/{id}.{extension}`. With no CDN configured `url` is null; API authorization does not secure public CDN URLs. Upload, CDN object protection and remote object deletion are not implemented here. Deletion removes the SQL sound and dependent rows, invalidates caches and attempts local-file cleanup; failure to clean up a local file is logged after the database deletion.

## Collections and search

| Method | Path | Parameters / result |
| --- | --- | --- |
| GET | `/guilds/{guildId}/sounds` | `period`, `timezone`; `{guildId, counts:{all,mine,others}, tags, summary, sounds}` |
| GET | `/sounds/me` | `period`, `timezone`; `{counts:{all}, tags, summary, guilds:{guildId:{name,sounds}}}` |
| GET | `/sounds/{soundId}` | One canonical Sound |
| GET | `/sounds` | At least one of `q`, `tag`; `{sounds,limit}` |
| GET | `/tags` | Required prefix `q`; `{tags:[{id,name}]}` |

Guild lists contain only sounds originally uploaded in that guild. They require ordinary membership and bot presence, not administrator access. Personal lists include all sounds uploaded by the caller and group by original guild; the group name is null if the caller no longer belongs. Lists are complete, ordered by global plays descending and numeric ID ascending. `tags` contains `{id,name,soundCount}` for all sounds in the collection.

Guild summary counts only plays of those sounds recorded in that guild during the interval. Personal summary counts plays globally for the caller's uploaded sounds. Both provide `period`, `plays`, `listeners`, `myPlays`, `likes`, `dislikes`, `soundCount`, `averagePlaysPerListener`, `previousPlays` and `playsChangePercent`. Likes/dislikes are current global totals, not interval events. Listener totals are distinct across the entire collection.

Search accepts a literal case-insensitive substring `q` and exact case-insensitive tag name `tag`, combined with AND. Empty/blank values do not satisfy the required filter. `sort` is `plays` (default), `likes` or `dislikes`; `order` is `desc` (default) or `asc`; `limit` is 1–100, default 100. Query/tag length is at most 255 characters. Search has no pagination in v1. Visibility: public sounds, caller-owned sounds, and private sounds from guilds the caller belongs to. The same visibility applies to detail and prefix tag search. Tag search is alphabetical, maximum 100, and does not create tags.

## Analytics

| Method | Path | Meaning |
| --- | --- | --- |
| GET | `/guilds/{guildId}/sounds/analytics` | Sounds uploaded in the guild, played in that same guild |
| GET | `/sounds/me/analytics` | Caller-uploaded sounds; collection summary plus one sound analytics result per sound |
| GET | `/sounds/{soundId}/analytics` | One sound globally, optionally restricted using `guildId` |

All accept `period=7d|30d|90d|all` (default `30d`) and `timezone` (default `Europe/Rome`, an IANA ZoneId). `from` is inclusive, `to` exclusive, timestamps UTC. `all` has a null `from`, null previous-play comparison, and daily series from the first recorded play. Change is null when the previous count is zero. An empty collection has zero totals and no sound entries.

Generic analytics include total plays, distinct users, average plays per user, daily series, 168 weekday/hour heatmap cells, source distribution and per-user replay-count buckets (1, 2–5, 6–20, 21–50, 51+). Weekdays are ISO 1 Monday to 7 Sunday; hours 0–23. Days and cells with no plays are zero-filled. Java groups UTC quarter-hours before conversion to the requested zone, preserving modern quarter/half-hour offsets and daylight-saving transitions without requiring MariaDB timezone tables. SQL sessions use UTC and restore the borrowed connection's original timezone before returning it to the shared pool.

Guild analytics also include top five sounds, top five most-liked sounds, aggregate user leaderboard and a result per sound. User leaderboard entries contain display identity, distinct sound count and play count, never timestamps or individual sources. Source names retain `AudioType` meanings: `AUDIO`, `SOUND`, `GREET`, `SOUNDBOARD`, with `UNKNOWN` for unrecognized historical values. Existing AUDIO rows are not reclassified.

Single-sound analytics return `{soundId,period,timezone,from,to,totals,guilds,otherGuilds,my}`. `totals` and each guild group contain the aggregate distributions. Accessible guilds with plays appear under their IDs with aggregate user counts. Nonmember guilds collapse into one anonymous `otherGuilds`, without guild IDs or user identities. Cross-guild totals deduplicate users. Ownership does not grant access to other users' timestamps.

`my` has `{plays,history,nextCursor}`. Only the authenticated caller's individual history is returned, at most 100 events ordered by `(time DESC,id DESC)`. Each event contains `{id,playedAt,guildId,source}`. `guildId` is null for a guild the caller cannot access. The next page is fetched from `/sounds/{soundId}/analytics` using `cursor` and the original `period`, `timezone` and optional `guildId`. The cursor binds caller, sound, scope and original interval; totals continue to describe the full interval. Collection endpoints include the first page per sound. A complete personal history of sounds outside the selected collection is out of scope.

All analytics use one streaming SQL history read per block of up to 500 sound IDs, with grouping in Java; raw plays are never exported to the client. Current and previous intervals are read together. Only aggregate maps and the newest 101 personal events per sound are retained, rather than a list of all history rows. There is no analytics cache in v1. Personal history pages and collection summaries reuse the same pass; there is no per-sound history query or per-sound aggregate query. No per-guild or temporal likes/dislikes are inferred: the interaction table stores only current state.

## Writes

`PATCH /sounds/{soundId}` accepts a nonempty object with any of `name`, `public`, `tags`. Omitted fields stay unchanged. Name is trimmed, nonblank, at most 100 characters, not numeric-only; duplicates in the existing uploader/original-guild namespace are rejected. Public is boolean. Tags replace the set, `[]` clears it, at most five nonblank names of at most 255 characters, normalized to lowercase and deduplicated. Unknown/read-only fields and nulls are rejected. Only the uploader may edit. The entire patch is atomic. Response is the updated canonical Sound.

`DELETE /sounds/{soundId}` permits the uploader or an Administrator/owner of the original guild. Manage Server alone is insufficient for deletion. Response is 204 with no body.

`PATCH /sounds/{soundId}/like` and `/dislike` accept exactly `{ "enabled": true|false }`. Enabling replaces the opposite vote. Disabling removes only that vote and leaves an opposite vote intact. Writes lock the sound and atomically update interaction and counts. Response: `{soundId,likes,dislikes,likedByMe,dislikedByMe}`. Requests are idempotent.

## Profile and greet settings

Existing `GET /me` keeps `user` and administrative `servers` unchanged and adds `greets:{scope:Sound}`. Scope `0` is global; other keys are explicit guild overrides. Only configured entries are returned, with canonical Sound values, not inherited copies. Deleted or no-longer-visible target sounds are omitted. The full greeting table is read, not only the lazy user cache.

`POST /me/settings` accepts exactly one top-level `greets` object. Values are string sound IDs or null to delete; omitted keys stay unchanged. A parsed request returns HTTP 200 and independent `{status,errors}` results per scope. SQL failure for one scope does not roll back successful others.

```json
{ "greets": { "0": "42", "123": "73", "789": null } }
```

Global: public sound or caller-owned private. The latter is explicitly permitted globally outside its original guild. Guild-specific: caller membership and bot presence; a private must originate in that exact guild, including when owned by the caller; a public may originate elsewhere. Removing a guild override restores global fallback. Removing global leaves no global fallback.

Validation is repeated against current sound visibility at playback. Explicit guild setting takes precedence; otherwise global. Invalid configured targets are not played. The bot's successful greet writes update UserData only after persistence succeeds; API writes invalidate that user's local cache after commit. The existing cached administrative guild list is never used as proof of ordinary membership.

Bulk has no product limit on scope count. The caller's memberships are resolved once per request. Each write locks/revalidates its sound in an independent transaction; no HTTP request is made per sound. Very large HTTP payloads remain subject to deployment resources and server timeouts; 100,000 scopes are not a measured guarantee.

## Cache and compatibility

`SoundCache` remains the API facade and canonical loader. Its typed Caffeine backing has 100-entry capacity and expires 60 seconds after write. Bulk results retain request-local references so evictions never truncate a response. Missing IDs are omitted from bulk loading and produce 404 for detail.

`SoundQueryCache` stores only ordered ID arrays in Redis, for 60 seconds, including empty results. Keys include version, caller, current membership set, original guild/ownership scope, normalized filters, ordering and limit. A catalog generation token makes old selections unreachable after metadata/vote changes or deletion; older keys expire naturally. Inserts and Discord mutations use the same invalidation path. Play-based rankings may lag by at most 60 seconds. Redis failure falls back to SQL. Cached IDs are rechecked against fresh sound metadata before response; they never authorize a private sound.

All SQL reads for one facade call share a read-only repeatable-read connection; schema selection and timezone setup/restoration happen once per read request. Collection tags are loaded once and reused for objects and tag counts. Response copies use fresh metadata, global counts and batched personal enrichment. No database operations happen inside JSON getters. There is no public response containing a cached user's private state. Cache invalidation is local for object/UserData caches; Redis generation is shared. This does not introduce a cross-instance pub/sub protocol.

## Errors and deployment

General errors use `{code,message}`: 400 invalid input, 401 credentials, 403 guild/write permissions, 404 absent or invisible sound, 503 Discord/storage unavailable. Malformed JSON and invalid typed query parameters return the same sanitized error family. Bulk greet item errors use their per-scope result, including storage outages.

Before deployment stop the previous application, apply `database/berbit/sound/001-sound-history-guild.sql` if the column is absent, then `002-sound-api.sql`, then start the updated application. No scripts run automatically. Inspect `SHOW CREATE TABLE` first: historical schema dumps omit counters and define a guild foreign key incompatible with global greet scope 0. The second script adds missing counters, rebuilds them from history/interactions, adds query indexes and removes that guild FK; it keeps the sound FK. Its DDL targets MariaDB. See [history](../sound-history.md).

The fixed historical public guild ID is treated as a real stored value, as agreed. Historical assignments are approximate. LoL API, Redis namespaces and Discord visual presentation are unchanged. The only new bot behavior is shared invalidation, transactional sound data updates and the agreed greet validity rules.

## History query cost and diagnostics

For a nonempty collection of up to 500 sounds, guild and personal analytics execute three data queries with an ID-cache miss (ID selection, sound metadata, streaming history), or two with an ID-cache hit. Single-sound analytics execute two (sound metadata and history). These counts exclude authentication, connection/session statements and any retry after invalid cached IDs. Larger collections are read in bounded ID blocks, on the same connection.

The history read selects `id, sound_id, user_id, guild_id, time, source` and filters by selected sounds, upper bound, optional lower bound covering the previous interval, and optional guild. There is no GROUP BY, SQL sort, or personal-history query per sound. MariaDB Connector/J fetch size is 256; processing completes before any further query is issued on that connection, following the [driver streaming guidance](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j). Network transfer still scales with the selected play count; Java keeps grouped counts and bounded personal queues, not all events. High-cardinality user/group counts and all-time requests can still be expensive.

Debug logs report membership resolution time (`Sound memberships`), history rows and processing time (`Sound history`), and total facade read time (`Sound read`). Membership resolution occurs before the read timer; authentication is also outside that timer. These are diagnostics, not an assertion that the live latency has been resolved.

## Validation evidence

Regression tests cover sound contracts, HTTP/auth integration, cache expiry/capacity, query-cache fallback, privacy, concurrency, transactions, cursors, DST and greets. A query-budget test distributes 10,000 plays over 60 sounds and checks one history query and one connection for both guild and personal analytics, plus one tag query for the guild library. A separate H2 test streams 1,000,000 plays through the full Java single-sound analytics, checks totals, anonymous guild grouping and the bounded personal page, and verifies index use with EXPLAIN. This is an H2 feasibility check, not a MariaDB latency guarantee. MariaDB migration execution, live Redis/Discord/Cloudflare integration and extreme bulk HTTP payloads have not been verified.

The migration uses [MariaDB ALTER TABLE](https://mariadb.com/docs/server/reference/sql-statements/data-definition/alter/alter-table) conditionals; it is not advertised as a portable MySQL/H2 migration.
