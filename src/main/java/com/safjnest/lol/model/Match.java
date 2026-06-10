package com.safjnest.lol.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameModeType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameType;
import no.stelar7.api.r4j.basic.constants.types.lol.MapType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.match.v5.ObjectiveStats;

public class Match implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public int id;
    public String gameId;
    public LeagueShard leagueShard;
    public GameQueueType queue;
    public Map<TeamType, Integer> bans = new HashMap<>();
    public transient JSONObject events = new JSONObject();
    public long timeStart;
    public long timeEnd;
    public String patch;
    public long gameCreation;
    public int gameDuration;
    public GameModeType gameMode;
    public String gameName;
    public GameType gameType;
    public MapType map;
    public String tournamentCode;
    public List<Participant> participants = new ArrayList<>();
    public List<Team> teams = new ArrayList<>();

    public Match() {}

    public static Match fromR4J(LOLMatch raw) {
        Objects.requireNonNull(raw, "raw");

        Match result = new Match();
        result.gameId = String.valueOf(raw.getGameId());
        result.leagueShard = raw.getPlatform();
        result.queue = raw.getQueue();
        result.patch = raw.getGameVersion();
        result.gameCreation = valueOrZero(raw.getGameCreation());
        result.gameDuration = valueOrZero(raw.getGameDuration());
        result.timeStart = raw.getGameStartTimestamp() != null
            ? raw.getGameStartTimestamp()
            : result.gameCreation;
        result.timeEnd = raw.getGameEndTimestamp() != null
            ? raw.getGameEndTimestamp()
            : result.timeStart + (result.gameDuration * 1000L);
        result.gameMode = raw.getGameMode();
        result.gameName = raw.getGameName();
        result.gameType = raw.getGameType();
        result.map = raw.getMap();
        result.tournamentCode = raw.getTournamentCode();

        if (raw.getParticipants() != null) {
            for (no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant participant : raw.getParticipants()) {
                result.participants.add(Participant.fromR4J(participant));
            }
        }

        if (raw.getTeams() != null) {
            for (MatchTeam team : raw.getTeams()) {
                Team converted = Team.fromR4J(team);
                result.teams.add(converted);

                Integer firstBan = null;
                for (Ban ban : converted.bans) {
                    if (ban.championId == -1) continue;
                    firstBan = ban.championId;
                    break;
                }
                result.bans.put(converted.team, firstBan);
            }
        }
        return result;
    }

    /**
     * @return match duration in milliseconds
     */
    public long getDuration() {
        return timeEnd - timeStart;
    }

    public long getGameId() {
        if (gameId == null || gameId.isBlank()) return 0;
        int separator = gameId.lastIndexOf('_');
        String value = separator == -1 ? gameId : gameId.substring(separator + 1);
        return Long.parseLong(value);
    }

    public LeagueShard getPlatform() {
        return leagueShard;
    }

    public GameQueueType getQueue() {
        return queue;
    }

    public long getGameCreation() {
        return gameCreation != 0 ? gameCreation : timeStart;
    }

    public int getGameDuration() {
        if (gameDuration != 0) return gameDuration;
        return (int) (getDuration() / 1000L);
    }

    public Duration getGameDurationAsDuration() {
        return Duration.ofSeconds(getGameDuration());
    }

    public long getGameStartTimestamp() {
        return timeStart;
    }

    public long getGameEndTimestamp() {
        return timeEnd;
    }

    public String getGameVersion() {
        return patch;
    }

    public GameModeType getGameMode() {
        return gameMode;
    }

    public String getGameName() {
        return gameName;
    }

    public GameType getGameType() {
        return gameType;
    }

    public MapType getMap() {
        return map;
    }

    public String getTournamentCode() {
        return tournamentCode;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public List<Team> getTeams() {
        return teams;
    }

    @Serial
    private void writeObject(ObjectOutputStream output) throws IOException {
        output.defaultWriteObject();
        output.writeObject(events == null ? null : events.toString());
    }

    @Serial
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        String eventsJson = (String) input.readObject();
        events = eventsJson == null || eventsJson.isBlank()
            ? new JSONObject()
            : new JSONObject(eventsJson);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    public static class Team implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public TeamType team;
        public boolean win;
        public List<Ban> bans = new ArrayList<>();
        public Map<String, Objective> objectives = new LinkedHashMap<>();

        public Team() {}

        public static Team fromR4J(MatchTeam raw) {
            Objects.requireNonNull(raw, "raw");

            Team result = new Team();
            result.team = raw.getTeamId();
            result.win = raw.didWin();

            if (raw.getBans() != null) {
                for (ChampionBan ban : raw.getBans()) {
                    result.bans.add(Ban.fromR4J(ban));
                }
            }
            if (raw.getObjectives() != null) {
                for (Map.Entry<String, ObjectiveStats> entry : raw.getObjectives().entrySet()) {
                    result.objectives.put(entry.getKey(), Objective.fromR4J(entry.getValue()));
                }
            }
            return result;
        }

        public TeamType getTeamId() {
            return team;
        }

        public boolean didWin() {
            return win;
        }

        public List<Ban> getBans() {
            return bans;
        }

        public Map<String, Objective> getObjectives() {
            return objectives;
        }
    }

    public static class Ban implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public int championId;
        public int pickTurn;

        public Ban() {}

        public static Ban fromR4J(ChampionBan raw) {
            Objects.requireNonNull(raw, "raw");

            Ban result = new Ban();
            result.championId = raw.getChampionId();
            result.pickTurn = raw.getPickTurn();
            return result;
        }

        public int getChampionId() {
            return championId;
        }

        public int getPickTurn() {
            return pickTurn;
        }
    }

    public static class Objective implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public boolean first;
        public int kills;

        public Objective() {}

        public static Objective fromR4J(ObjectiveStats raw) {
            Objects.requireNonNull(raw, "raw");

            Objective result = new Objective();
            result.first = raw.isFirst();
            result.kills = raw.getKills();
            return result;
        }

        public boolean isFirst() {
            return first;
        }

        public int getKills() {
            return kills;
        }
    }
}
