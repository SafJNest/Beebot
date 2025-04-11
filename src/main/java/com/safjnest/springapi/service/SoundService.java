package com.safjnest.springapi.service;

import com.safjnest.springapi.api.model.Sound;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryRecord;

public class SoundService {

    public Sound getSoundById(String id) {
        QueryRecord sound = DatabaseHandler.getSoundById(id);
        if (sound == null) {
            return null;
        }
        return new Sound(
            sound.getAsInt("id"),
            sound.get("name"),
            sound.get("guild_id"),
            sound.get("user_id"),
            sound.get("extension"),
            sound.getAsInt("public") == 1,
            sound.getAsDate("time").toLocalDate()
        );
    }
}
