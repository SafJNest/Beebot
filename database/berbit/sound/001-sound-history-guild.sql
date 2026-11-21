ALTER TABLE sound_history
  ADD COLUMN guild_id varchar(19) DEFAULT NULL AFTER user_id,
  ADD KEY guild_id (guild_id);

UPDATE sound_history AS history
JOIN sound ON sound.id = history.sound_id
SET history.guild_id = CASE
  WHEN sound.public = 0 THEN sound.guild_id
  ELSE '474935164451946506'
END
WHERE history.guild_id IS NULL;
