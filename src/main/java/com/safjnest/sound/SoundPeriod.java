package com.safjnest.sound;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.sql.QueryRecord;
import static com.safjnest.sound.SoundValues.*;

public record SoundPeriod(String period, ZoneId zone, Instant from, Instant to, Instant before, String beforeId) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static SoundPeriod of(String period, String timezone, String cursor, String soundId, String userId, String guildId) {
        try {
            int days = switch (period) {
                case "7d" -> 7;
                case "30d" -> 30;
                case "90d" -> 90;
                case "all" -> 0;
                default -> throw bad("Invalid period");
            }
            ;
            ZoneId zone = ZoneId.of(timezone);
            Instant to = Instant.now(), before = null;
            String beforeId = null;
            if (cursor != null) {
                if (cursor.length() > 2048) throw bad("Invalid cursor");
                var node = JSON.readTree(Base64.getUrlDecoder().decode(cursor));
                if (!node.path("user").asText().equals(userId) || !node.path("sound").asText().equals(soundId)
                || !node.path("guild").asText().equals(guildId == null ? "" : guildId)
                || !node.path("period").asText().equals(period) || !node.path("zone").asText().equals(zone.getId())) throw bad("Cursor does not match request");
                to = Instant.parse(node.path("to").asText());
                before = Instant.parse(node.path("before").asText());
                beforeId = id(node.path("id").asText());
                Long.parseLong(beforeId);
                if (to.isAfter(Instant.now()) || !before.isBefore(to)) throw bad("Invalid cursor interval");
            }
            Instant from = days == 0 ? null : to.minus(Duration.ofDays(days));
            if (before != null && from != null && before.isBefore(from)) throw bad("Invalid cursor interval");
            return new SoundPeriod(period, zone, from, to, before, beforeId);
        }
        catch (com.safjnest.auth.AuthException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw bad("Invalid period, timezone or cursor");
        }
    }

    public SoundPeriod previous() {
        if (from == null) return null;
        return new SoundPeriod(period, zone, from.minus(Duration.between(from, to)), from, null, null);
    }

    public String cursor(QueryRecord row, String soundId, String userId, String guildId) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(map("user", userId, "sound", soundId,
            "guild", guildId == null ? "" : guildId, "period", period, "zone", zone.getId(), "to", to.toString(),
            "before", row.getAsTimestamp("time").toInstant().toString(), "id", row.get("id"))));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
