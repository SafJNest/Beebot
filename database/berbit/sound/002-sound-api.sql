ALTER TABLE sound
  ADD COLUMN IF NOT EXISTS plays int NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS likes int NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS dislikes int NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS sound_history_sound_time ON sound_history (sound_id, time);
CREATE INDEX IF NOT EXISTS sound_history_guild_time_sound ON sound_history (guild_id, time, sound_id);
CREATE INDEX IF NOT EXISTS sound_history_user_sound_time ON sound_history (user_id, sound_id, time);

-- Global greetings use guild_id = '0', which is not a Discord guild.
ALTER TABLE greeting DROP FOREIGN KEY IF EXISTS greeting_relation_1;

UPDATE sound AS s
SET plays = (SELECT COUNT(*) FROM sound_history h WHERE h.sound_id = s.id),
    likes = (SELECT COUNT(*) FROM sound_interactions i WHERE i.sound_id = s.id AND i.value = 1),
    dislikes = (SELECT COUNT(*) FROM sound_interactions i WHERE i.sound_id = s.id AND i.value = -1);
