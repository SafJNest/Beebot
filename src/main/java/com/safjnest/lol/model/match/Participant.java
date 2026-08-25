package com.safjnest.lol.model.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class Participant {
    public int id;
    public int summonerId;
    public int matchId;
    public boolean win;
    public String kda;
    public int champion;
    public LaneType lane;
    public TeamType team;
    public int roleQuestId;
    public RankProgress rankProgress;
    public int damage;
    public int damageTaken;
    public int damageBuilding;
    public int healing;
    public int cs;
    public int goldEarned;
    public int ward;
    public int wardKilled;
    public int visionScore;
    public HashMap<String, Integer> pings = new HashMap<>();
    public int subTeam;
    public int subTeamPlacement;
    public String puuid;
    public String riotId;
    public String riotTag;

    public int level;

    public int doubles;
    public int triples;
    public int quadruples;
    public int pentas;

    public int item0;
    public int item1;
    public int item2;
    public int item3;
    public int item4;
    public int item5;
    public int item6;
    public int turretKills;

    public int q;
    public int w;
    public int e;
    public int r;
    public int d;
    public int f;

    public int summonerSpell1;
    public int summonerSpell2;

    public List<Integer> primaryRunes = new ArrayList<>();
    public List<Integer> secondaryRunes = new ArrayList<>();
    public List<Integer> statsRunes = new ArrayList<>();

    public List<Integer> skillOrder = new ArrayList<>();

    public List<Integer> augments = new ArrayList<>();

    public List<Integer> starterItems = new ArrayList<>();
    public List<Integer> buildPath = new ArrayList<>();
    public int boots;
    public int supportItem;

    public int championId() { return champion; }
    public String puuid() { return puuid; }
    public String team() { return team != null ? team.name() : null; }

    public static Participant forMatchResult(int championId, String puuid, String team) {
        Participant participant = new Participant();
        participant.champion = championId;
        participant.puuid = puuid;
        participant.team = parseTeam(team);
        return participant;
    }

    private static TeamType parseTeam(String value) {
        if (value == null || value.isBlank()) return null;
        try { return TeamType.valueOf(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
