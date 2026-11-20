# Beebot asset storage on R2

Add the `r2` block to the existing `settings` object in `rsc/settings.json`.
This is local deployment configuration. Restart Beebot after editing it.

```json
"r2": {
  "enabled": true,
  "cdnUrl": "https://cdn.example.com",
  "soundPrefix": "sounds/",
  "soundDirectory": "rsc/cache/sounds",
  "soundBucket": "",
  "accountId": "",
  "accessKeyId": "",
  "secretAccessKey": ""
}
```

Use the existing CDN base URL and match `soundPrefix` to the uploaded object
keys. The default prefix is `sounds/`. An empty prefix gives audio keys such
as `123/123.mp3` and cover keys such as `123/cover.png`. Requests use the same
CDN base for `/assets/punto.jpg`. Upload, CDN playback, bot downloads and
metadata edits use `sounds/<id>/<id>.<ext>`. MariaDB records that key in
`sound.url`.

The CDN URL is sufficient for reading. To enable uploads, fill `soundBucket`,
`accountId`, `accessKeyId`, and `secretAccessKey` with the R2 S3 credentials for
that bucket. With all credentials empty, CDN reads work and upload attempts
fail without committing a sound row. Keep these values in the ignored local
settings file. See [Cloudflare's R2 token documentation](https://developers.cloudflare.com/r2/api/tokens/).

When `enabled` is false or the block is absent, audio commands use the legacy
local directory `rsc/sounds` and the API uses the existing Spring `CDN_URL`.

Playback, replay, soundboard buttons, greets and Discord downloads share the
same local file directory. On a miss, the bot finishes the download in a
staging file before publishing the local copy. Simultaneous requests for the
same file share the result. The local copy remains on the VPS.

The bot stages the Discord attachment and measures its duration with the same
local media detection used by the player. It inserts the sound and its tags in
one SQL transaction, uploads the object with the generated ID and commits before
acknowledging success. A failed upload rolls back the sound and tag writes.
The [multipart sound upload API](api/beebot-sounds.md#upload-a-sound) uses the
same storage path. The dashboard caps the file at 3.9 MB to leave room for
multipart headers within the [Vercel Edge request limit](https://vercel.com/docs/functions/runtimes/edge/edge-functions.rsc).
Discord's playing embed and all Sound API responses read `sound.duration_ms`.

R2 object metadata contains `id`, `guild-id`, `user-id`, `extension`, `public`,
`created-at`, `duration-ms`, `plays`, `likes`, `dislikes`, `name-base64`,
`description-base64` when present, and `tags-gzip-base64`. The name and
description are UTF-8 encoded as unpadded Base64URL. Tags are
gzip-compressed UTF-8 JSON with ID and name, then encoded as unpadded Base64URL.
The object metadata is a snapshot on upload and edit. MariaDB owns
the live play and vote counts and the underlying history. Metadata edits write
SQL and update the existing R2 object's metadata before committing. A failed
R2 update rolls back the edit. If R2 succeeds and SQL commit fails, object
metadata can diverge until the next successful edit. A failed creation commit
can leave an orphan object. Sound deletion removes the catalog entry and keeps
both the VPS file and the R2 object. Sound uploads require a nullable
`sound.duration_ms` column of type `BIGINT`.

## Soundboard thumbnails

Saved soundboards use the same `soundBucket` and CDN. The object key is
`soundboard/thumbnail/<soundboardId>/<uuid>.<ext>` for PNG, JPEG or GIF. A new
attachment is limited to 1 MiB. Each replacement assigns a fresh URL so it can
be served immediately. The row stores `thumbnail` after upload succeeds. A
failed upload leaves the previous cover in place, and rejects creation if the
soundboard is new. Older R2 objects are retained.

Both soundboard display commands read the nullable `soundboard.thumbnail`
column and show the bot avatar when the key is missing.
The [soundboard API](api/beebot-sounds.md#soundboards) reads the same key and
uses the same format checks and upload routine when replacing a cover.

External images and response-specific attachments retain their existing sources.

## Sound audio and covers

Audio and covers share the same bucket and CDN as soundboard thumbnails.
For sound `1` with the default prefix, the audio is `sounds/1/1.mp3`.
Its cover, when present, uses `sounds/1/<uuid>.<ext>` and the same size and
format validation as soundboard thumbnails. MariaDB stores the cover key in
`sound.thumbnail`.
These database columns contain object keys relative to the configured CDN.
The API builds the full URLs returned to clients.
Catalog access still follows SQL visibility. A direct CDN link can be
opened by anyone who has it.
