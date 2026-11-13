# Sound play history

New sound plays record the guild where playback was requested in `sound_history.guild_id`, independently of the sound's original guild. Command, search, replay, soundboard and greet plays use the same history. The existing source and user meanings remain unchanged.

For an existing database, stop the old application, apply `database/berbit/sound/001-sound-history-guild.sql`, then apply `002-sound-api.sql` (counters, indexes and global-greet FK compatibility), and then start the new version. This prevents the old version from inserting unassigned plays after the backfill. The migration fills existing rows with the sound's original guild when it is currently private and `474935164451946506` when it is currently public. This is an approximation: visibility may have changed, and a private sound may have been used outside its original guild. The fixed ID is intentionally treated as a normal stored guild ID in analytics; any historical misattribution is accepted. No provenance column is added. Rows already assigned a guild are not overwritten.

The [Sounds API](api/beebot-sounds.md) reads these records for aggregates and the caller's paginated history. Discord presentation remains unchanged. Sound writes and counters are transactional; greet playback enforces the agreed public/private scope rules. No migration runs automatically.
