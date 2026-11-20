# Beebot dashboard authentication

The base URL is `https://api.safjnest.com/api/beebot`. A servlet filter protects every `/api/beebot` route. The exact POST endpoints `/auth/discord/authorize` and `/auth/session`
require a generic `X-API-Key` and run before login. Requests under
`/api/beebot/public/` require an API key and accept a generic key without
a session. A supplied bearer is always checked on those public routes.
The remaining routes accept a generic key and a session bearer, or a
personal key by itself. A personal key also identifies the caller on
the public routes. See the [frontend contract](beebot-sound-community.md)
for route permissions.

Qwik generates a random URL-safe state and stores it in a short-lived Secure HttpOnly cookie. Its server calls `POST /auth/discord/authorize` with JSON `{ "state": "...", "redirectUri": "https://safjnest.com/auth/callback/" }`. The response `{ "authorizationUrl": "...", "expiresIn": 600 }` redirects the browser to Discord with scopes `identify guilds`. The exact callback is associated with the application key and must also be registered with Discord.

Discord redirects the browser to Qwik's callback with its temporary code and state in the query string. Qwik checks state against its cookie and calls `POST /auth/session` server-to-server with `X-API-Key` and JSON `{ "code": "...", "state": "...", "redirectUri": "..." }`. Beebot consumes the state atomically, checks the same application and callback, exchanges the code with Discord and returns `{ "sessionToken": "...", "expiresAt": 1790000000000, "user": { "id": "...", "username": "...", "globalName": null, "avatarUrl": "https://cdn.discordapp.com/..." } }`. `expiresAt` is epoch milliseconds; the example value is illustrative. Qwik sets the session token as a host-only Secure HttpOnly cookie whose expiration does not exceed `expiresAt`, and forwards it from server routes. Qwik and its reverse proxy must redact the Discord callback query from logs and use a restrictive referrer policy.

## Profile and guild list

`GET /me` returns exactly:

```json
{
  "user": { "id": "42", "username": "tester", "globalName": null, "avatarUrl": null },
  "servers": [{ "id": "1", "name": "Guild", "iconUrl": null, "owner": true }],
  "greets": {}
}
```

`MeController` owns `/me` and `/me/settings`; credential and session behavior is unchanged. `greets` maps configured scope IDs (`"0"` globally) to canonical Sound objects. `POST /me/settings` updates these settings with independent results per scope; see [Sounds and greet settings](beebot-sounds.md). Ordinary membership for audio is verified separately from the administrative server list.

`GET /guilds` returns a JSON array directly (`[{ "id": "1", "name": "Guild", "iconUrl": null, "owner": true }]`); `GET /servers` remains an alias with the same array response. There is no `{servers: ...}` wrapper on these two list endpoints. All three routes use the same authorized list for the authenticated session. Owners and members with Administrator **or Manage Server** permission are included. OAuth lists may include guilds without the bot; personal keys list only guilds shared with the bot. `iconUrl` is a Discord HTTPS CDN URL or null. The profile comes from the session/issued personal key, with no extra `/users/@me` request per page.

`GuildController` owns `/guilds` and `/servers`, while `DiscordAuthController`
owns only the three authentication POST endpoints. `DiscordAuthService` owns
both session operations and the shared authorized directory, using the existing
`DiscordGuildCache`; there is no separate guild service. HTTP URLs, array shape,
profile shape and permission policy are unchanged.

The guild list is cached for 60 seconds. Keys include the user, application, session identity and a hash of the encrypted Discord credential; separate users, sessions and changed credentials cannot reuse each other's entries. Redis is preferred, with an in-memory fallback that retains the original expiry. A Redis miss after recovery never trusts an older memory entry. Concurrent requests share a single load per cache identity; Redis generation/lease checks coordinate separate application instances and fence invalidated or expired loaders. During a Redis outage, coordination is local to each process. A new session or changed token uses a new identity; logout invalidates the old identity and any in-flight publication. There is currently no token-refresh endpoint; any future in-place token replacement must invalidate the previous context before replacing it. Errors are not cached or automatically retried.

For OAuth, a cache miss performs `GET /users/@me/guilds?limit=200`; Discord documents 200 as the maximum guild count of a non-bot user, so no extra empty pagination request is made. Personal keys use JDA member caches first. Complete member caches can establish absence locally; an incomplete cache triggers a member lookup only when the member is missing. Unknown membership is excluded, while lookup failures fail the request rather than publishing a partial list.

`POST /auth/logout` revokes the session (or personal key), invalidates the list and returns 204; Qwik clears its cookie. API-key and session revocation are still checked against SQL on every protected request. Guild permission changes may take up to 60 seconds to be reflected.

All responses use `Cache-Control: no-store`. Authentication errors use `{ "code": "...", "message": "..." }`, adding `retryAfter` only for rate limits:

| Failure | HTTP | Code |
| --- | --- | --- |
| Discord 429 | 503 | `discord_rate_limited`, with numeric `retryAfter` in seconds and an integer `Retry-After` header rounded up |
| Discord 5xx or network failure | 503 | `discord_unavailable` |
| Discord user token rejected with 401/403 | 401 | `discord_token_expired` |
| Authentication SQL/configuration/encryption unavailable | 503 | `auth_unavailable` |
| Guild settings storage unavailable | 503 | `settings_unavailable` |

Malformed inputs return 400, invalid or revoked credentials 401, forbidden callbacks or guild access 403. Invalid Discord payloads or rejected OAuth code exchanges return 502 `discord_unavailable`. A revoked Discord access token is detected on the next remote load. Non-2xx Discord responses log status, rate-limit bucket, remaining requests and retry delay at warn level; credentials are never included.

The filter and controller advice share `spring.exception.ApiErrors` and the
single `ApiExceptionHandler`. Application failures use
`com.safjnest.exception.Exception` without Spring dependencies. Retry seconds and
the rounded-up header are now returned by the filter as well as the advice.
Malformed bodies, invalid parameters, unknown routes, unsupported methods and
unexpected errors under `/api/beebot` use this same Beebot envelope; routing and
generic framework failures no longer fall through to the LoL error shape.
Unexpected failures return only `internal_error` / `Unexpected server error`.

## Setup and operations

Review and apply `database/auth/001-dashboard-auth.sql` before enabling the feature. This manual script creates the dedicated MariaDB database `auth` and its tables. If `auth` already has `api_key` and `auth_session`, apply `database/auth/002-oauth-transaction.sql` if `auth_transaction` is missing, then apply `database/auth/003-api-key-types.sql` to add the key type and personal identity columns. Fresh installations using the current `001` already include those columns; `003` is idempotent. `AuthDB` selects `auth` in both normal and test mode. The existing MariaDB connection account needs access to this database. Existing tables in `berbit` or `website` are not copied or modified; migrate any existing authentication records explicitly before switching. Configure `api.discord` with the client ID, OAuth secret, existing encryption key (Base64 of 32 bytes), and comma-separated exact redirect URLs. See [configuration](../configuration.md#discord-oauth-and-r2). Legacy deployments still read `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`, `AUTH_ENCRYPTION_KEY`, and `AUTH_ALLOWED_REDIRECT_URIS` from `spring.properties`. Redirect URLs are copied into each new API key's SQL metadata at creation; changes to the file require a restart and do not alter existing clients. HTTPS is required except for explicitly allowed localhost or 127.0.0.1. Keep credentials outside source control.

The `!test generate apikey <name>` command, restricted to Untouchables and Epria, creates an application key and sends it once in a private Discord message. A failed private delivery attempts revocation and never publishes the key. `!test revoke apikey <name>` revokes the application and invalidates its cached entry. Names are unique and contain letters, digits, `_` or `-`. A revoked name cannot be reused. `!test cleanup auth` deletes up to 1000 expired sessions, revoked sessions, or sessions belonging to revoked applications, and up to 1000 expired OAuth transactions; repeat if either count reaches 1000. These commands must be run by an operator; no migration or cleanup is automatic.

`AuthDB` owns SQL access for application status, sessions and OAuth transactions, stored in `auth.api_key`, `auth.auth_session` and `auth.auth_transaction`. OAuth state is stored only as a SHA-256 hash, tied to the client and callback, expires after 600 seconds, and is consumed by one atomic SQL delete. Stored API and session tokens are also SHA-256 hashes; Discord access tokens use AES-256-GCM, and Discord refresh tokens are not stored. A session lasts at most eight hours or until the Discord token expires. API keys are looked up by SHA-256 hash in SQL on every request, including their current type and identity. Redis caches generic user-session data for at most 60 seconds and never beyond session expiry. Session cache hits check authoritative SQL status, preventing stale refill from undoing revocation. Redis outage falls back to SQL throughout login and session handling. SQL outage returns 503. All instances must share SQL and the encryption key; Redis remains an optional cache. Encryption-key rotation requires users to log in again. Cleanup and key creation never log or display the Discord token.

`AuthDB` maps key and session reads by column name through `QueryRecordParser`.
Full session reads and the `SELECT 1` check on cache hits apply the same SQL
conditions. The existing HTTP status codes and response bodies apply to both
paths.

## Guild configuration

`GET /guilds/{guildId}` and `POST /guilds/{guildId}/settings` use the same application/session authentication and check the requested guild ID against the same authorized list used by `/me` and `/guilds`. See [Beebot guild settings](beebot-guilds.md) for fields, independent update results and alert operational status.

## Generic and personal API keys

Both types live in the same `auth.api_key` table and use the same format: 43 random URL-safe characters. The API does one lookup by `SHA-256(X-API-Key)` and switches on the stored `type`:

| `type` | Authentication |
| --- | --- |
| `generic` | The key identifies an application; protected routes additionally require `Authorization: Bearer <sessionToken>`. |
| `personal` | The key itself authenticates the stored `discord_user_id`, with its profile in `user_json`; no separate bearer token is needed. |

`api_key` contains `id`, `name`, `key_hash`, `type`, `redirect_uris`, nullable `discord_user_id`, nullable `user_json`, and active/revocation metadata. Generic keys leave personal identity fields null. The `id` of a personal row also identifies its permanent session; personal authentication does not create an `auth_session` row. The existing `auth_session` and `auth_transaction` tables remain dedicated to generic OAuth login. There is no separate personal-key table or key-prefix routing.

For existing installations apply `database/auth/003-api-key-types.sql` before running the updated authentication code, including when only generic keys are used. It alters `api_key`, defaulting existing keys to `generic`. The initial `001` schema includes the same columns. No migration, deployment or credential issuance is automatic.

API key commands, restricted to `PermissionHandler.isUntouchable` or `isEpria`:

```text
!test generate apikey <name>
!test revoke apikey <name>
!test generate personalkey <name>
!test revoke personalkey <name>
```

Personal generation takes the author's verified Discord ID and profile directly from the command event; it does not accept an arbitrary target user ID. Both creation paths insert into `api_key`, specifying their type. Key names share one unique namespace across both types and cannot be reused after revocation. The revoke commands check the requested type. Keys are delivered once in a private Discord message; failed private delivery attempts revocation. The original key is never stored in SQL or printed in server channels/logs.

The personal mapping is `SHA-256(API key) -> api_key row (type=personal) -> Discord user ID`. There is no expiry. In the internal `AuthContext`, `personal()` derives from `client.type()`, `sessionHash` carries the key row ID, `encryptedToken` is null and `expiresAt` is `Long.MAX_VALUE`. The `/me` profile is the verified profile captured at issuance. No OAuth token is required or retained for this path.

Send only the personal key:

```http
GET /api/beebot/me
X-API-Key: <personalKey>
```

The same header works for `GET /guilds`, its `/servers` alias, `GET /guilds/{guildId}` and `POST /guilds/{guildId}/settings`. Omit `Authorization`: combining a personal key with a bearer token returns 401. Personal keys cannot start or exchange OAuth login transactions (403 on the pre-session routes).

Every key lookup reads authoritative SQL, including the current type. Revocation takes effect on subsequent requests without waiting for cache expiry. `POST /auth/logout` revokes the personal key. Invalid/revoked keys return 401; unavailable SQL returns 503. Personal keys are not removed by generic OAuth session cleanup.

For personal requests, the shared guild-list cache uses JDA as described above. Guild GET/POST use that same list and permission policy. Personal keys do not grant global administrator rights or bypass guild permissions. The source-specific choice is confined to loading data: controllers always authenticate, resolve the user context, authorize and execute the requested method.

The `test` key generation and revocation operations are available only to Untouchables or Epria; denied attempts receive a private message. Other `test` operations remain restricted to the bot owner and co-owners.


Discord HTTP now uses the common `HttpUtils` transport and GET/form builders;
`DiscordApi` retains the provider-specific URLs, JSON parsing, authorization and
error policy. The 15-second timeouts, explicit UTF-8, form fields/escaping,
non-following of redirects and retry metadata are preserved. Parsing a retry
header reuses `ParseUtils`, with finite/non-negative checks still in DiscordApi.
The change is internal: login/session routes, credentials, response shapes and
SQL/cache revocation behavior are unchanged. Transport tests use simulated
responses; they do not establish live Discord availability.
