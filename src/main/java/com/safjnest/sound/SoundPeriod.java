package com.safjnest.sound;

import static com.safjnest.sound.SoundValues.bad;
import static com.safjnest.sound.SoundValues.id;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.discord.auth.AuthException;

public record SoundPeriod(String period, ZoneId zone, Instant from, Instant to, Instant before, String beforeId) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CURSOR_LENGTH = 2048;

    public static SoundPeriod of(String period, String timezone, String cursor, String soundId, String userId, String guildId) {
        try {
            int days = switch (period) {
                case "7d" -> 7;
                case "30d" -> 30;
                case "90d" -> 90;
                case "all" -> 0;
                default -> throw bad("Invalid period");
            };

            ZoneId zone = ZoneId.of(timezone);
            Instant to = Instant.now();
            Instant before = null;
            String beforeId = null;

            if (cursor != null) {
                if (cursor.length() > MAX_CURSOR_LENGTH)
                    throw bad("Invalid cursor");

                JsonNode node = JSON.readTree(Base64.getUrlDecoder().decode(cursor));
                if (!node.path("user").asText().equals(userId)
                        || !node.path("sound").asText().equals(soundId)
                        || !node.path("guild").asText().equals(guildId == null ? "" : guildId)
                        || !node.path("period").asText().equals(period)
                        || !node.path("zone").asText().equals(zone.getId()))
                    throw bad("Cursor does not match request");

                to = Instant.parse(node.path("to").asText());
                before = Instant.parse(node.path("before").asText());
                beforeId = id(node.path("id").asText());
                Long.parseLong(beforeId);

                if (to.isAfter(Instant.now()) || !before.isBefore(to))
                    throw bad("Invalid cursor interval");
            }

            Instant from = days == 0 ? null : to.minus(Duration.ofDays(days));
            if (before != null && from != null && before.isBefore(from))
                throw bad("Invalid cursor interval");

            return new SoundPeriod(period, zone, from, to, before, beforeId);
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw bad("Invalid period, timezone or cursor");
        }
    }

    public SoundPeriod previous() {
        if (from == null)
            return null;

        Instant previousStart = from.minus(Duration.between(from, to));

        return new SoundPeriod(period, zone, previousStart, from, null, null);
    }

    public String cursor(String eventId, Instant playedAt, String soundId, String userId, String guildId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("user", userId);
        values.put("sound", soundId);
        values.put("guild", guildId == null ? "" : guildId);
        values.put("period", period);
        values.put("zone", zone.getId());
        values.put("to", to.toString());
        values.put("before", playedAt.toString());
        values.put("id", eventId);

        try {
            byte[] encoded = JSON.writeValueAsBytes(values);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
