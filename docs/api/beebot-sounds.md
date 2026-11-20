# Sounds, analytics and personal greet settings

The base path is `/api/beebot`. Authenticated routes use an application key
and user session, or a personal key. Public catalog routes accept an
application key with an optional user session. Responses use
`Cache-Control: no-store`. IDs are strings. See [authentication](beebot-auth.md),
[request and response examples](beebot-sounds-examples.json), and
[OpenAPI](beebot-sounds.openapi.json).

## Canonical Sound

Existing sound detail, library and greet responses contain the `com.safjnest.sound.model.Sound`. Response copies carry viewer-specific data; cached objects never carry a viewer identity. JSON fields are explicit, so paths, audio tracks, padded empty tags and operational getters are not exposed.

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
  "durationMs": 2088,
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

Counts in Sound are global/all time. `listeners` counts distinct users associated with recorded plays, not everyone present in the voice channel. `myPlays` counts the caller's recorded plays. Uploader display data comes from the Discord cache; unavailable names are `Unknown`, avatars null. `durationMs` is the playback duration from MariaDB, in milliseconds. It is available immediately after upload, including on a cold cache, and is null when the row has no persisted duration. `createdAt` can be null for legacy records.

With R2 enabled, the HTTP `url` combines the CDN base with the canonical key `sounds/{id}/{id}.{extension}`. The bot resolves the same key on a local-file cache miss. API authorization governs metadata responses and the CDN serves audio directly. With R2 disabled, Spring's `CDN_URL` supplies the legacy URL and the bot uses `rsc/sounds`. With neither configured, `url` is null. See [R2 configuration and file handling](../audio-storage-r2.md).

Deletion removes the sound and dependent rows from SQL and invalidates the metadata and selection caches. The R2 object and the VPS file are retained.

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

Guild analytics also include top five sounds, top five most-liked sounds, aggregate user leaderboard and a result per sound. User leaderboard entries contain display identity, distinct sound count and play count, never timestamps or individual sources. Source names retain `AudioType` meanings: `AUDIO`, `SOUND`, `GREET`, `SOUNDBOARD`, `WEB`, with `UNKNOWN` for unrecognized historical values. Existing AUDIO rows are not reclassified.

Single-sound analytics return `{soundId,period,timezone,from,to,totals,guilds,otherGuilds,my}`. `totals` and each guild group contain the aggregate distributions. Accessible guilds with plays appear under their IDs with aggregate user counts. Nonmember guilds collapse into one anonymous `otherGuilds`, without guild IDs or user identities. Cross-guild totals deduplicate users. Ownership does not grant access to other users' timestamps.

`my` has `{plays,history,nextCursor}`. Only the authenticated caller's individual history is returned, at most 100 events ordered by `(time DESC,id DESC)`. Each event contains `{id,playedAt,guildId,source}`. `guildId` is null for a guild the caller cannot access. The next page is fetched from `/sounds/{soundId}/analytics` using `cursor` and the original `period`, `timezone` and optional `guildId`. The cursor binds caller, sound, scope and original interval; totals continue to describe the full interval. Collection endpoints include the first page per sound. A complete personal history of sounds outside the selected collection is out of scope.

All analytics use one streaming SQL history read per block of up to 500 sound IDs, with grouping in Java; raw plays are never exported to the client. `SoundAnalytics` owns calculation and response composition; its package-private `SoundHistory` accumulator keeps typed counts indexed by sound/guild, plus bounded personal events. Daily series, heatmap, source/replay distribution and leaderboard have separate builders. Source ordinals resolve through the existing `AudioType` enum, with `UNKNOWN` for invalid stored values. Current and previous intervals are read together. Only aggregate maps and the newest 101 personal events per sound are retained, rather than a list of all history rows. There is no analytics cache in v1. Personal history pages and collection summaries reuse the same pass; there is no per-sound history query or per-sound aggregate query. No per-guild or temporal likes/dislikes are inferred: the interaction table stores only current state.

## Writes

### Upload a sound

`POST /guilds/{guildId}/sounds` accepts `multipart/form-data`. Send an MP3 or Opus in `file` and an `application/json` part named `metadata`. The caller must belong to the guild and share it with the bot. Beebot derives the uploader from authentication. `name` is required (maximum 32 characters), `description` is optional, `public` defaults to true and `tags` defaults to an empty list. The direct API accepts files up to 25 MiB.

```sh
curl -X POST "$BEEBOT_BASE/guilds/123/sounds" \
  -H "X-API-Key: $BEEBOT_API_KEY" \
  -H "Authorization: Bearer $BEEBOT_SESSION" \
  -F 'file=@airhorn.mp3;type=audio/mpeg' \
  -F 'metadata={"name":"Airhorn","public":true,"tags":["arcade"]};type=application/json'
```

`201 Created` includes `Location: /api/beebot/sounds/42` and a canonical `Sound` response. The shared Discord and HTTP upload path validates the audio, persists the measured duration, writes the file and commits before returning success. A file storage failure rolls back the catalog write.

```json
{
  "id": "42",
  "name": "Airhorn",
  "guildId": "123",
  "userId": "456",
  "uploader": {"id":"456","name":"Luna","avatarUrl":null},
  "public": true,
  "createdAt": "2026-11-18T10:00:00Z",
  "url": "https://cdn.example.com/sounds/42.mp3",
  "durationMs": 2088,
  "tags": [{"id":"1","name":"arcade"}],
  "plays": 0,
  "listeners": 0,
  "likes": 0,
  "dislikes": 0,
  "myPlays": 0,
  "likedByMe": false,
  "dislikedByMe": false,
  "ownedByMe": true,
  "canEdit": true,
  "canDelete": true,
  "soundboardCount": 0
}
```

### Edit, delete, and vote

`PATCH /sounds/{soundId}` accepts a nonempty object with any of `name`, `description`, `public`, `tags`. Omitted fields stay unchanged. Name validation is shared with upload. Names are trimmed, nonblank, non-numeric and limited to 32 characters. Previously stored longer names remain readable. Duplicates in the existing uploader/original-guild namespace are rejected. Public is boolean. Tags replace the set, `[]` clears it, at most five nonblank names of at most 255 characters, normalized to lowercase and deduplicated. Only `description` accepts `null`. A blank description also clears it. Upload and edit trim descriptions and accept up to 500 Unicode code points. Only the uploader may edit. The entire patch is atomic. Response is the updated canonical Sound.

Apply the [sound schema cutover](#schema-cutover) before running the updated backend. Existing sounds have `description: null` in both the dashboard and public responses until the uploader adds text.

`DELETE /sounds/{soundId}` permits the uploader or an Administrator/owner of the original guild. Manage Server alone is insufficient for deletion. Response is 204 with no body.

`PATCH /sounds/{soundId}/like` and `/dislike` accept exactly `{ "enabled": true|false }`. Enabling replaces the opposite vote. Disabling removes only that vote and leaves an opposite vote intact. Writes lock the sound and atomically update interaction and counts. Response: `{soundId,likes,dislikes,likedByMe,dislikedByMe}`. Requests are idempotent.

## Soundboards

Guild routes require membership and bot presence. Creation records the caller as uploader. Rename, sound changes, cover replacement and board deletion require the uploader or the guild owner or Administrator. An ID under a different guild returns 404. A board can contain public sounds and private sounds belonging to its own guild. Detail responses use canonical `Sound` objects filtered by current sound visibility. `soundCount` counts stored associations and can exceed the number of visible sounds in the detail.

| Call | Request body | Success response |
| --- | --- | --- |
| `GET /guilds/123/soundboards` | Empty | `200`, array of boards |
| `GET /guilds/123/soundboards/7` | Empty | `200`, `{board,sounds}` |
| `POST /guilds/123/soundboards` | `{"name":"Arcade","soundIds":["42"]}` | `201`, `{board,sounds}` and `Location: /api/beebot/guilds/123/soundboards/7` |
| `PATCH /guilds/123/soundboards/7` | `{"name":"Arcade remix"}` | `200`, updated `{board,sounds}` |
| `PUT /guilds/123/soundboards/7/sounds/42` | Empty | `204 No Content` |
| `DELETE /guilds/123/soundboards/7/sounds/42` | Empty | `204 No Content` |
| `PUT /guilds/123/soundboards/7/thumbnail` | Multipart part `file` | `200`, `{"thumbnailUrl":"https://cdn.example.com/soundboard/thumbnail/7/ddea3344-4853-433d-ac8e-ad97aba7b919.png"}` |
| `DELETE /guilds/123/soundboards/7` | Empty | `204 No Content` |

Example `GET /guilds/123/soundboards` response.

```json
[
  {"id":"7","guildId":"123","userId":"456","name":"Arcade","thumbnailUrl":null,"soundCount":1,"canEdit":true}
]
```

For detail, creation and rename, `board` is the same object and `sounds` is an array of the full canonical upload response shape above. An empty board detail, for example after removing the last sound, is valid.

```json
{
  "board": {"id":"7","guildId":"123","userId":"456","name":"Arcade","thumbnailUrl":null,"soundCount":0,"canEdit":true},
  "sounds": []
}
```

Creation takes 1–25 distinct IDs. A duplicate name or an addition above the limit returns 409. Adding an existing association or removing an absent one is safe to retry. Removing the final sound through the API keeps the board. Deleting the board removes its associations and preserves the individual sounds and the existing cover object.

```sh
curl -X PUT "$BEEBOT_BASE/guilds/123/soundboards/7/thumbnail" \
  -H "X-API-Key: $BEEBOT_API_KEY" \
  -H "Authorization: Bearer $BEEBOT_SESSION" \
  -F 'file=@cover.png;type=image/png'
```

The cover accepts PNG, JPEG and GIF up to 1 MiB. Follow the [R2 storage contract](../audio-storage-r2.md) for file retention and credentials.

## Profile and greet settings

Existing `GET /me` keeps `user` and administrative `servers` unchanged and adds `greets:{scope:Sound}`. Scope `0` is global; other keys are explicit guild overrides. Only configured entries are returned, with canonical Sound values, not inherited copies. Deleted or no-longer-visible target sounds are omitted. The canonical `UserData` instance completes its greet loading through `getAllGreets()`; a full-load flag distinguishes this snapshot from scopes loaded lazily. The returned map is copied.

`POST /me/settings` accepts exactly one top-level `greets` object. Values are string sound IDs or null to delete; omitted keys stay unchanged. A parsed request returns HTTP 200 and independent `{status,errors}` results per scope. SQL failure for one scope does not roll back successful others.

```json
{ "greets": { "0": "42", "123": "73", "789": null } }
```

Global: public sound or caller-owned private. The latter is explicitly permitted globally outside its original guild. Guild-specific: caller membership and bot presence; a private must originate in that exact guild, including when owned by the caller; a public may originate elsewhere. Removing a guild override restores global fallback. Removing global leaves no global fallback.

Validation is repeated against current sound visibility at playback. Explicit guild setting takes precedence; otherwise global. Invalid configured targets are not played. Bot and API greet writes update the same `UserData` instance only after persistence succeeds. Own writes retain the cached object; playback resolves the same settings and rechecks current sound visibility. The existing cached administrative guild list is never used as proof of ordinary membership.

Bulk has no product limit on scope count. The caller's memberships are resolved once per request. Each write locks/revalidates its sound in an independent transaction; no HTTP request is made per sound. Very large HTTP payloads remain subject to deployment resources and server timeouts; 100,000 scopes are not a measured guarantee.

## Cache and compatibility

`SoundCache` remains the canonical loader and read facade. HTTP parsing belongs to `SoundController` and `MeController`; mutations belong to `Sound` and `UserData`, with prepared SQL in `BotDB`. The former `SoundDB` implementation has been absorbed into `BotDB`. Its instance-local Caffeine backing now reuses `bot.cache.CacheAdapter`, with 100-entry capacity and expiration 60 seconds after write. Single/bulk and preloaded API rows share coordinated loads of missing IDs. Scoped callbacks retain active entries across expiration/eviction, and invalidation prevents an older load from repopulating the cache. Bulk results retain request-local references so evictions never truncate a response. Missing IDs are omitted from bulk loading and produce 404 for detail. Response copies still use fresh authorized SQL rows and never put viewer-specific state on a cached object. This changes internal coordination only; HTTP routes, payloads and authorization remain unchanged.

`SoundCache` stores only ordered ID arrays in Redis, for 60 seconds, including empty results. Keys include version, caller, current membership set, original guild/ownership scope, normalized filters, ordering and limit. A catalog generation token makes old selections unreachable after metadata/vote changes or deletion; older keys expire naturally. Inserts and Discord mutations use the same invalidation path. Metadata/vote writes keep the canonical Sound object and invalidate only derived selections after commit. A deletion marks the object and removes its entry. Play-based rankings may lag by at most 60 seconds. Redis failure falls back to SQL. Cached IDs are rechecked against fresh sound metadata before response; they never authorize a private sound.

All SQL reads for one facade call share a read-only repeatable-read connection; schema selection and timezone setup/restoration happen once per read request. Collection tags are loaded once and reused for objects and tag counts. Personal play counts and current votes use the named prepared reads `BotDB.readSoundUserPlays()` and `readSoundInteraction()`, including when called by the bot model; `Sound` contains no SQL literals. Missing data gives zero plays/no vote, while storage failures remain errors. Response copies use fresh metadata, global counts and batched personal enrichment. No database operations happen inside JSON getters. There is no public response containing a cached user's private state. Object-cache coordination is local; Redis generation is shared. This does not introduce a cross-instance pub/sub protocol.

`Sound.deferEdit()` makes a private metadata copy. Its setters do not write SQL;
`update()` saves name, visibility and the complete tag set in one transaction,
then publishes to the original object. A normal setter performs that save
immediately. Revision checks reject stale drafts. A successful metadata save
consumes its draft even when writing identical values. API mutations refresh
current metadata under the object's monitor before preparing the edit, preserving
omitted PATCH fields. A refresh advances the revision only if name, visibility
or the tag set changed; tag order and empty slots are ignored in this comparison.
Equivalent refreshes, including those before votes or rejected/failed requests,
do not invalidate another pending draft. Changes to play/vote counters alone do
not invalidate it either. Vote, play-history and deletion operations use dedicated transactions
with the existing SQL ownership/visibility locks. SQL failure leaves the prior
object state intact. `/me` and `/me/settings` now belong to `MeController`; their
URLs and response shapes are unchanged.

`Tag` is an immutable value with internal empty/new/stored predicates: an empty
name represents an unused slot, ID zero with a name represents a new tag, and a
positive ID with a name represents a stored tag. Saved tags replace the originals
only after commit. The bot model retains five slots for its existing Discord
buttons; public JSON omits empty slots and still exposes only name and string ID,
without these internal predicates. No HTTP/OpenAPI or Discord presentation change
is required by this internal cleanup.

Discord membership and display identity lookup now belong to `DiscordApi`,
shared with authentication. Ordinary membership remains separate from the
administrative directory, and Manage Server alone still does not permit sound
deletion. JDA cache loading, `Unknown`/null-avatar fallbacks and response identity
fields are unchanged. `SoundAccess` has been removed after migrating its callers.

## Errors and deployment

General errors use `{code,message}`: 400 invalid input, 401 credentials, 403 guild/write permissions, 404 absent or invisible sound, 503 Discord/storage unavailable. Malformed JSON and invalid typed query parameters return the same sanitized error family. Bulk greet item errors use their per-scope result, including storage outages.

`com.safjnest.exception.Exception` carries application failures; HTTP adaptation
is shared by `AuthFilter` and `ApiExceptionHandler` through `ApiErrors`. The
Beebot envelope also covers absent routes and framework errors, with retry
metadata preserved in both filter and advice. This does not alter sound success
payloads, OpenAPI schemas, independent greet results or Discord presentation.

Before deployment stop the previous application, apply `database/berbit/sound/001-sound-history-guild.sql` if the column is absent, then `002-sound-api.sql` if that migration has not been applied, then follow the [community cutover](#schema-cutover) before starting the updated application. No scripts run automatically. Inspect `SHOW CREATE TABLE` first: historical schema dumps omit counters and define a guild foreign key incompatible with global greet scope 0. The second script adds missing counters, rebuilds them from history/interactions, adds query indexes and removes that guild FK; it keeps the sound FK. Its DDL targets MariaDB. The migration backfills `sound_history.guild_id` with the sound's original guild when private and `474935164451946506` when public; this approximation is accepted and existing assigned rows are not overwritten.

The fixed historical public guild ID is treated as a real stored value, as agreed. Historical assignments are approximate. LoL API, Redis namespaces and Discord visual presentation are unchanged. The only new bot behavior is shared invalidation, transactional sound data updates and the agreed greet validity rules.

## History query cost and diagnostics

For a nonempty collection of up to 500 sounds, guild and personal analytics execute three data queries with an ID-cache miss (ID selection, sound metadata, streaming history), or two with an ID-cache hit. Single-sound analytics execute two (sound metadata and history). These counts exclude authentication, connection/session statements and any retry after invalid cached IDs. Larger collections are read in bounded ID blocks, on the same connection.

The history read selects `id, sound_id, user_id, guild_id, channel_id, time, source` and filters by selected sounds, upper bound, optional lower bound covering the previous interval, and optional guild. There is no GROUP BY, SQL sort, or personal-history query per sound. MariaDB Connector/J fetch size is 256; processing completes before any further query is issued on that connection, following the [driver streaming guidance](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j). Network transfer still scales with the selected play count; Java keeps grouped counts and bounded personal queues, not all events. High-cardinality user/group counts and all-time requests can still be expensive.

Debug logs report membership resolution time (`Sound memberships`), history rows and processing time (`Sound history`), and total facade read time (`Sound read`). Membership resolution occurs before the read timer; authentication is also outside that timer. These are diagnostics, not an assertion that the live latency has been resolved.

## Sound community backend

`SoundCommunityController` exposes the catalog, comments, votes, web
plays and sound images via `SoundService`. The [frontend HTTP contract](beebot-sound-community.md)
covers authentication, filters and both public and viewer-specific
responses. Legacy reads and PATCH vote routes are still available to
existing dashboard callers and are marked deprecated in OpenAPI.

- `SoundDB` shares BotDB's prepared statements, read session and lock on the
  sound row. Bot, current HTTP and community vote writes all use `sound_vote`.
  The transaction changes one user vote and applies its delta to the current
  counters. Clearing a vote removes the row.
- Web playback uses `sound_play` and appends ordinal `WEB` to `AudioType`.
  A retry with the same UUID and sound ID reuses the event. An anonymous event
  contributes to play totals, time series and source counts. Listener counts,
  replay distribution, personal history and per-listener averages use
  identified users. New Discord records can store the voice channel ID.
- Comment threads use a direct parent ID. Each write checks that the parent
  belongs to the same sound in the transaction. All comments are owned by the
  sound through a foreign key, so deleting a sound can cascade independently of
  thread depth. Deleting a comment keeps a placeholder while it has replies.
  Removing the last reply prunes any now-empty deleted ancestors.
- Root and reply pages default to 20 rows. The first root page reserves one slot
  for the pinned comment. Pin replacement, reply counts and the sound's total
  comment count are transaction-protected. One query reads the page and its
  viewer votes. Catalog pages load tags and listener/viewer data in batches.
- Community visibility is public plus caller-owned private sounds. The catalog,
  tag popularity and creator totals use public rows. Matching personal results
  are a separate first-page section, including private uploads. Web comments, votes and plays all check the same visibility policy.
  Catalog cards use the configured CDN for both media URLs.

### Schema cutover

Inspect the product database with `SHOW CREATE TABLE sound`,
`SHOW CREATE TABLE sound_history`, and `SHOW CREATE TABLE sound_interactions`.
Run the new [003 migration](../../database/berbit/sound/003-sound-community.sql)
after the historical migrations, in one MariaDB session with the old bot and
API stopped. Apply it to a restored copy first and record the totals before
and after. The new foreign keys use the actual type of `sound.id`. The migration adds
`audio_key` and `image_key`. The application uses the canonical object layout
described in the [R2 storage contract](../audio-storage-r2.md#sound-audio-and-covers).

For databases using the original community column names, apply
[004](../../database/berbit/sound/004-sound-description.sql) and
[005](../../database/berbit/sound/005-sound-columns.sql) before starting the
updated application. The backend reads `sound.url`, `sound.thumbnail`,
`sound.comments`, `sound_comment.user_id`, `sound_comment.replies` and
`soundboard.thumbnail`. The URL and thumbnail columns still store relative
R2 object keys. The HTTP contract keeps its current field names.

If an old soundboard table has both `thumbnail_key` and the legacy BLOB
`thumbnail`, first rename the BLOB with
`ALTER TABLE soundboard RENAME COLUMN thumbnail TO thumbnail_legacy`.
The 005 migration then renames the key column and preserves both values.
For a database such as canary with all current column names, apply only
the `MODIFY COLUMN description` to `utf8mb4` when the column inherits
`latin1`. The current names already satisfy the backend queries.

`sound_history` is renamed to `sound_play` in place. Old IDs, times, sources
and assigned guilds carry over. `channel_id` and `playback_id` start as null.
Existing active votes are copied from `sound_interactions`, the counters
are reconciled, and the source table is preserved as
`sound_interactions_before_003`. Previous neutral votes have no row in
`sound_vote`. The script can resume before the application restarts, and a
repeat after the backup rename leaves the active vote table intact. Keep
the backup for the cutover. Use a database backup to reverse a deployment
after writes have resumed.

\`\`\`sql
SELECT COUNT(*) AS plays, MIN(id) AS first_id, MAX(id) AS last_id FROM sound_play;
SELECT COUNT(*) AS source_votes FROM sound_interactions_before_003 WHERE value IN (-1, 1);
SELECT COUNT(*) AS migrated_votes FROM sound_vote WHERE sound_id IS NOT NULL;
SELECT COUNT(*) AS mismatched_votes
FROM sound_interactions_before_003 old
LEFT JOIN sound_vote new ON new.sound_id=old.sound_id AND new.user_id=old.user_id
WHERE old.value IN (-1, 1) AND (new.id IS NULL OR new.value<>old.value);
\`\`\`

### Owner test command

`!test sound` lists the operations. Examples include `!test sound explore`,
`!test sound search arcade`, `!test sound suggest ar`,
`!test sound comments 42` and `!test sound comments 42 7`. For another page,
supply the parent ID or `-` for roots, followed by the returned cursor.
`post`, `reply`, `edit`, `pin`, `heart`, `delete`, `vote` and `play`
write only with the test database configuration. Repeating
`!test sound play 42 <uuid>` checks deduplication. The command reports
results to the owner in a direct message.

## Validation evidence

The previous regression suite covered sound contracts, HTTP/auth integration, cache expiry/capacity, query-cache fallback, privacy, concurrency, transactions, cursors, DST and greets; its 101 test files were removed during the owner’s repository cleanup. The cache and shared-mutation steps add focused local tests for coordinated bulk loading, expiration/eviction, invalidation, loader failures, hydration, prepared SQL with H2 and viewer-state isolation. The historical checks below are previous evidence, not a current re-execution of that deleted suite. A query-budget test distributes 10,000 plays over 60 sounds and checks one history query and one connection for both guild and personal analytics, plus one tag query for the guild library. A separate H2 test streams 1,000,000 plays through the full Java single-sound analytics, checks totals, anonymous guild grouping and the bounded personal page, and verifies index use with EXPLAIN. This is an H2 feasibility check, not a MariaDB latency guarantee. Local community validation uses MariaDB 11.8.10 with disposable schemas. The 003 migration was exercised with signed SMALLINT and unsigned INT sound IDs, preserved play history, copied active votes, enforced vote targets and playback uniqueness, and kept new votes when repeated. The Java service was also exercised against MariaDB for catalog visibility, comment cursors, votes, retries and deletion. Live Redis, Discord, Cloudflare and production migration execution remain outside this verification.

The migration uses [MariaDB ALTER TABLE](https://mariadb.com/docs/server/reference/sql-statements/data-definition/alter/alter-table) conditionals; it is not advertised as a portable MySQL/H2 migration.


The shared HTTP/parsing cleanup changes no sound route, response, query, cache
key or Discord presentation. Discord membership still uses the same JDA flow;
provider HTTP classification remains in `DiscordApi` over the shared transport.
The existing full analytics response comparisons remain in the local test suite;
no sound OpenAPI/schema update is required by this internal change.
