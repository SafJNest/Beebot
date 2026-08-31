package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.RecordMetric;

import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public final class ProfileRecordAnalyzer {

    private ProfileRecordAnalyzer() {}

    public static Accumulator accumulator(String puuid, Filter filter) {
        return new Accumulator(puuid, filter);
    }

    public static final class Accumulator {
        private final String puuid;
        private final String filterKey;
        private final EnumMap<RecordMetric, ProfileRecord> records = new EnumMap<>(RecordMetric.class);

        private Accumulator(String puuid, Filter filter) {
            this.puuid = puuid;
            filterKey = filter.toSummonerKey();
        }

        public void accept(Match match) {
            Participant player = participant(match, puuid);
            if (player == null) return;

            add(match, player, RecordMetric.KILLS, player.kills, null, null);
            add(match, player, RecordMetric.DEATHS, player.deaths, null, null);
            add(match, player, RecordMetric.ASSISTS, player.assists, null, null);
            add(match, player, RecordMetric.PENTAKILLS, player.pentas, null, null);
            add(match, player, RecordMetric.CS, player.cs, null, null);
            add(match, player, RecordMetric.DAMAGE_DEALT, player.damage, null, null);
            if (player.damageTaken != null) add(match, player, RecordMetric.DAMAGE_TAKEN, player.damageTaken, null, null);
            if (match.getDuration() > 0) add(match, player, RecordMetric.LONGEST_GAME, match.getDuration(), null, null);

            List<Map<String, Object>> kills = events(match, "champion_kills");
            List<Map<String, Object>> monsters = events(match, "monster_events");
            addFirstKill(match, player, kills);
            addFirstBlood(match, player, kills);
            addObjectiveKills(match, player, monsters);
            addTeamObjectives(match, player, monsters);
        }

        public List<ProfileRecord> finish() {
            long now = System.currentTimeMillis();
            List<ProfileRecord> result = new ArrayList<>(records.values());
            for (ProfileRecord record : result) record.lastUpdate = now;
            return result;
        }

        private void addFirstBlood(Match match, Participant player, List<Map<String, Object>> kills) {
            int playerId = participantId(match, player);
            for (Map<String, Object> event : kills) {
                if (!"first_blood".equals(string(event, "kill_type"))) continue;
                if (number(event, "killer") != playerId) continue;
                add(match, player, RecordMetric.FIRST_BLOOD_TIME, number(event, "timestamp"), null, null);
            }
        }

        private void addFirstKill(Match match, Participant player, List<Map<String, Object>> kills) {
            int playerId = participantId(match, player);
            for (Map<String, Object> event : kills) {
                if (number(event, "killer") != playerId) continue;
                add(match, player, RecordMetric.FIRST_KILL_TIME, number(event, "timestamp"), null, null);
            }
        }

        private void addObjectiveKills(Match match, Participant player, List<Map<String, Object>> monsters) {
            int playerId = participantId(match, player);
            int barons = 0;
            int elders = 0;
            for (Map<String, Object> event : monsters) {
                if (number(event, "killer") != playerId) continue;
                if (isBaron(event)) barons++;
                if (isElder(event)) elders++;
            }
            add(match, player, RecordMetric.BARON_KILLS, barons, null, null);
            add(match, player, RecordMetric.ELDER_KILLS, elders, null, null);
        }

        private void addTeamObjectives(Match match, Participant player, List<Map<String, Object>> monsters) {
            Map<String, Object> firstDrake = first(monsters, player.team, Accumulator::isDrake);
            Map<String, Object> firstBaron = first(monsters, player.team, Accumulator::isBaron);
            Map<String, Object> firstElder = first(monsters, player.team, Accumulator::isElder);

            addShared(match, player, RecordMetric.FIRST_DRAKE_TIME, firstDrake);
            addShared(match, player, RecordMetric.FIRST_BARON_TIME, firstBaron);
            addShared(match, player, RecordMetric.FIRST_ELDER_TIME, firstElder);
            add(match, player, RecordMetric.BARONS_TAKEN, count(monsters, player.team, Accumulator::isBaron), player.team, null);
            add(match, player, RecordMetric.ELDERS_TAKEN, count(monsters, player.team, Accumulator::isElder), player.team, null);
        }

        private void addShared(Match match, Participant player, RecordMetric metric, Map<String, Object> event) {
            if (event == null) return;
            Participant actor = participant(match, (int) number(event, "killer"));
            add(match, player, metric, number(event, "timestamp"), player.team, actor == null ? null : actor.puuid);
        }

        private void add(
            Match match,
            Participant player,
            RecordMetric metric,
            long value,
            TeamType team,
            String actorPuuid
        ) {
            if (value <= 0 || match == null || match.gameId == null || match.gameId.isBlank()) return;
            ProfileRecord candidate = ProfileRecord.from(
                puuid,
                filterKey,
                metric,
                value,
                match.gameId,
                match.timeStart,
                player,
                match.leagueShard,
                team,
                actorPuuid
            );
            ProfileRecord current = records.get(metric);
            if (current == null || before(candidate, current)) records.put(metric, candidate);
        }

        private static boolean before(ProfileRecord candidate, ProfileRecord current) {
            if (candidate.score != current.score) return candidate.score > current.score;
            if (candidate.occurredAt != current.occurredAt) return candidate.occurredAt < current.occurredAt;
            return candidate.matchId.compareTo(current.matchId) < 0;
        }

        private static Map<String, Object> first(
            List<Map<String, Object>> events,
            TeamType team,
            java.util.function.Predicate<Map<String, Object>> predicate
        ) {
            Map<String, Object> result = null;
            for (Map<String, Object> event : events) {
                if (!sameTeam(event, team) || !predicate.test(event)) continue;
                if (result == null || number(event, "timestamp") < number(result, "timestamp")) result = event;
            }
            return result;
        }

        private static int count(
            List<Map<String, Object>> events,
            TeamType team,
            java.util.function.Predicate<Map<String, Object>> predicate
        ) {
            int result = 0;
            for (Map<String, Object> event : events)
                if (sameTeam(event, team) && predicate.test(event)) result++;
            return result;
        }

        private static boolean isDrake(Map<String, Object> event) {
            return "DRAGON".equals(string(event, "monster")) && !isElder(event);
        }

        private static boolean isBaron(Map<String, Object> event) {
            return "BARON_NASHOR".equals(string(event, "monster"));
        }

        private static boolean isElder(Map<String, Object> event) {
            return "DRAGON".equals(string(event, "monster"))
                && "ELDER_DRAGON".equals(string(event, "subtype"));
        }

        private static boolean sameTeam(Map<String, Object> event, TeamType team) {
            return team != null && team.name().equals(string(event, "killer_team"));
        }

        private static List<Map<String, Object>> events(Match match, String name) {
            if (match == null || match.eventData == null || !(match.eventData.get(name) instanceof List<?> values)) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object value : values) if (value instanceof Map<?, ?> map) {
                Map<String, Object> event = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet())
                    if (entry.getKey() instanceof String key) event.put(key, entry.getValue());
                result.add(event);
            }
            return result;
        }

        private static Participant participant(Match match, String puuid) {
            if (match == null || match.participants == null || puuid == null) return null;
            for (Participant participant : match.participants)
                if (participant != null && puuid.equals(participant.puuid)) return participant;
            return null;
        }

        private static Participant participant(Match match, int id) {
            if (match == null || match.participants == null || id == 0) return null;
            for (Participant participant : match.participants)
                if (participant != null && participant.id == id) return participant;
            if (match.eventData == null || !(match.eventData.get("participants") instanceof Map<?, ?> values)) return null;
            Object value = values.get(String.valueOf(id));
            return value == null ? null : participant(match, String.valueOf(value));
        }

        private static int participantId(Match match, Participant player) {
            if (player == null) return 0;
            if (player.id > 0) return player.id;
            if (match == null || match.eventData == null || !(match.eventData.get("participants") instanceof Map<?, ?> values)) return 0;
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!player.puuid.equals(String.valueOf(entry.getValue()))) continue;
                try { return Integer.parseInt(String.valueOf(entry.getKey())); }
                catch (NumberFormatException ignored) { return 0; }
            }
            return 0;
        }

        private static long number(Map<String, Object> event, String key) {
            Object value = event.get(key);
            return value instanceof Number number ? number.longValue() : 0;
        }

        private static String string(Map<String, Object> event, String key) {
            Object value = event.get(key);
            return value == null ? null : String.valueOf(value);
        }
    }
}
