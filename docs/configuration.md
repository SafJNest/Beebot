# Beebot configuration

`Settings` is the shared entry point for the bot, HTTP server, databases and
integrations. It loads one YAML configuration at startup with Jackson. The file
contains `runtime`, `api`, `bot` and `services`. Each integration keeps its
settings type under `com.safjnest.config`.

## Choose the instance configuration

Copy [the example YAML](../beebot.example.yml) to `beebot.yml` in the project
root for local development. Git ignores `beebot.yml`. On the VPS, set
`BEEBOT_CONFIG` in each service to its own file, such as
`/etc/beebot/canary.yml` or `/etc/beebot/production.yml`. An explicit path takes
precedence over the local file. Relative paths start from the working directory.

List the bot profiles under `bot` and set `runtime.bot` to the profile to use,
such as `beebot` or `canary`. The loader reads the settings and credentials of
the selected bot. Restart Beebot after changing the profile. `runtime.testing`
controls test behavior independently of the bot profile.

For existing deployments, when both `BEEBOT_CONFIG` and `beebot.yml` are absent,
Beebot loads `config.properties`, `rsc/settings.json` and optional
`spring.properties`. Blank legacy OAuth and CDN properties still use their
existing environment variable names.

To migrate an instance, copy `testing` and `host` from `config.properties`
under `runtime`. Copy the profiles from `rsc/settings.json` → `bots` under
`bot` and set `runtime.bot` to the corresponding profile. Copy the settings
for current integrations under `services`. Set
`api.port` from `server.port` and `api.cdnUrl` from `CDN_URL` when used. Move the
effective Discord client ID, secret, encryption key and redirect URLs into
`api.discord`. Stop and start the service to apply the selected file.

Each new file sets `services.database` to the MariaDB host, credentials and
catalog for that instance. `runtime.testing` still selects the application's
test behavior. Deployments carrying a `services.testDatabase` block keep its
catalog selection in test mode. `api.port` configures the embedded Tomcat server
and defaults to `8080`. HTTP requests and responses keep the existing JSON
contract.

## Supply secrets as files

A YAML value such as `"credential:discord-bot-token"` reads the text file
`discord-bot-token` from `CREDENTIALS_DIRECTORY`. With systemd, supply each file
with `LoadCredential=` or `LoadCredentialEncrypted=` in the service unit.
For local development or another service manager, set
`BEEBOT_CREDENTIALS_DIRECTORY` to a directory readable by the service user.
On systemd, `CREDENTIALS_DIRECTORY` takes precedence. Keep credential files
outside the checkout and use separate service users or credentials per instance
where the environments are isolated.

The loader trims a final line ending. A missing or empty file reports its
credential name. Restart the process after replacing a credential or changing
the configuration. The application keeps the resolved configuration for the
life of the process. Existing files can use `credential:` references as well.

## Discord OAuth and R2

Set `api.discord.clientId`, `clientSecret`, `encryptionKey` and
`allowedRedirectUris` when enabling OAuth. Omit the `discord` section on an
instance without Discord OAuth. The encryption key is the Base64 encoding of
32 bytes. When moving an existing installation to the new format, preserve
the exact key bytes so active sessions remain readable. API keys and user
sessions continue to be managed by [AuthDB](api/beebot-auth.md#setup-and-operations).

Set `services.r2.enabled` and `cdnUrl` to enable CDN reads. Add `soundBucket`,
`accountId`, `accessKeyId` and `secretAccessKey` for uploads. `api.cdnUrl` is
the optional fallback for instances without R2. See [R2 storage](audio-storage-r2.md).
