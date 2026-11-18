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
keys. An empty prefix selects root-level objects such as `123.mp3`. The default
prefix is `sounds/`. Requests use the same CDN base for `/assets/punto.jpg`.
The CDN URL in API responses follows the same format for every sound.

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

An upload first stages the Discord attachment. SQL then inserts one row and
allocates the ID. The bot uploads the object with that ID before committing the
row and acknowledging success. An upload failure rolls back the SQL insert.
If R2 accepts a PUT and the subsequent SQL commit fails, the object may remain
without a row. Sound deletion affects the catalog, and existing files are
retained both on the VPS and in R2.

## Soundboard thumbnails

Saved soundboards use the same `soundBucket` and CDN. The object key is
`soundboard/thumbnail/<soundboardId>/<uuid>.<ext>` for PNG, JPEG or GIF. A new
attachment is limited to 1 MiB. Each replacement assigns a fresh URL so it can
be served immediately. The row stores `thumbnail_key` after upload succeeds. A
failed upload leaves the previous cover in place, and rejects creation if the
soundboard is new. Older R2 objects are retained.

To migrate existing covers:

1. Make sure the Beebot database has the nullable `soundboard.thumbnail_key`
   column and deploy the new bot to every instance writing to it. Both display
   commands can still render legacy covers during the migration.
2. Run `!migrate-soundboard-thumbnails --dry-run` as the bot owner in a channel
   using the server's command prefix. Run it without `--dry-run` to upload.
   The command reads the image bytes from MariaDB, leaves the BLOBs in place and
   skips rows with a key. It reports failed IDs and can be run again. A user
   replacement takes precedence over a migration of the same cover.
3. When the pending count is zero and the CDN URLs have been verified, the
   legacy `soundboard.thumbnail` column can be removed. The bot can display
   boards and report the pending count after the column is gone.

External images and response-specific attachments retain their existing sources.
