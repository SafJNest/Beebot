# Sound storage on Cloudflare R2

Sound uploads are stored in a Cloudflare R2 bucket through its S3-compatible API. Set these environment variables for the bot:

- `R2_ACCOUNT_ID`
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_BUCKET`

The token needs object read and write access to the selected bucket. The bot downloads sounds to a temporary local cache for playback and Discord downloads. Existing files in `rsc/sounds` are copied to R2 the first time each sound is accessed; new uploads go directly to R2, and deleting a sound also removes its R2 object. Legacy files are retained in the repository as a fallback.

When R2 credentials are absent, existing local sounds remain playable, but new uploads return an error. Configure the four variables before deploying this change so new sounds use R2.
