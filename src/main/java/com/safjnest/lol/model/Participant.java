package com.safjnest.lol.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.RoleType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchPerks;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;
import no.stelar7.api.r4j.pojo.lol.match.v5.StatPerk;

public class Participant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public int id;
    public int summonerId;
    public int matchId;
    public int participantId;
    public String riotSummonerId;
    public String riotIdName;
    public String riotIdTagline;
    public int profileIcon;
    public boolean win;
    public String kda;
    public int kills;
    public int deaths;
    public int assists;
    public int champion;
    public String championName;
    public LaneType lane;
    public LaneType championSelectLane;
    public LaneType gameDeterminedPosition;
    public RoleType role;
    public TeamType team;
    public TierDivisionType rank;
    public int gain;
    public int damage;
    public int damageBuilding;
    public int healing;
    public int totalDamageDealt;
    public int totalDamageDealtToChampions;
    public int physicalDamageDealtToChampions;
    public int magicDamageDealtToChampions;
    public int trueDamageDealtToChampions;
    public int damageDealtToObjectives;
    public int damageDealtToTurrets;
    public int damageSelfMitigated;
    public int totalDamageTaken;
    public int physicalDamageTaken;
    public int magicDamageTaken;
    public int trueDamageTaken;
    public int totalHeal;
    public int totalHealsOnTeammates;
    public int totalDamageShieldedOnTeammates;
    public int cs;
    public int totalMinionsKilled;
    public int neutralMinionsKilled;
    public int goldEarned;
    public int goldSpent;
    public int ward;
    public int wardKilled;
    public int visionScore;
    public HashMap<String, Integer> pings = new HashMap<>();
    public int subTeam;
    public int subTeamPlacement;
    public int placement;
    public String puuid;

    public int level;
    public int championExperience;
    public int championTransform;

    public int doubles;
    public int triples;
    public int quadruples;
    public int pentas;
    public int largestMultiKill;
    public int killingSprees;
    public int largestKillingSpree;

    public int item0;
    public int item1;
    public int item2;
    public int item3;
    public int item4;
    public int item5;
    public int item6;

    public int q;
    public int w;
    public int e;
    public int r;
    public int d;
    public int f;

    public int summonerSpell1;
    public int summonerSpell2;

    public List<Integer> primaryRunes   = new ArrayList<>();
    public List<Integer> secondaryRunes = new ArrayList<>();
    public List<Integer> statsRunes     = new ArrayList<>();

    public List<Integer> skillOrder = new ArrayList<>();

    public List<Integer> augments = new ArrayList<>();

    public List<Integer> starterItems = new ArrayList<>();
    public List<Integer> buildPath    = new ArrayList<>();
    public int boots;

    public int baronKills;
    public int dragonKills;
    public int inhibitorKills;
    public int inhibitorTakedowns;
    public int nexusKills;
    public int nexusTakedowns;
    public int turretKills;
    public int turretTakedowns;
    public int objectivesStolen;
    public int objectivesStolenAssists;

    public boolean eligibleForProgression;
    public boolean firstBloodAssist;
    public boolean firstBloodKill;
    public boolean firstTowerAssist;
    public boolean firstTowerKill;
    public boolean gameEndedInEarlySurrender;
    public boolean gameEndedInSurrender;
    public boolean teamEarlySurrendered;

    public int timePlayed;
    public int timeCCingOthers;
    public int totalTimeCCDealt;
    public int totalTimeSpentDead;
    public int bountyLevel;
    public int roleBoundItem;
    public Map<String, Object> challenges = new LinkedHashMap<>();

    public Participant() {}

    public static Participant fromR4J(MatchParticipant raw) {
        Objects.requireNonNull(raw, "raw");

        Participant result = new Participant();
        result.participantId = raw.getParticipantId();
        result.riotSummonerId = raw.getSummonerId();
        result.riotIdName = raw.getRiotIdName();
        result.riotIdTagline = raw.getRiotIdTagline();
        result.profileIcon = raw.getProfileIcon();
        result.puuid = raw.getPuuid();
        result.win = raw.didWin();
        result.kills = raw.getKills();
        result.deaths = raw.getDeaths();
        result.assists = raw.getAssists();
        result.kda = result.kills + "/" + result.deaths + "/" + result.assists;
        result.champion = raw.getChampionId();
        result.championName = raw.getChampionName();
        result.lane = raw.getLane();
        result.championSelectLane = raw.getChampionSelectLane();
        result.gameDeterminedPosition = raw.getGameDeterminedPosition();
        result.role = raw.getRole();
        result.team = raw.getTeam();
        result.subTeam = raw.getPlayerSubteamId();
        result.subTeamPlacement = raw.getSubteamPlacement();
        result.placement = raw.getPlacement();
        result.level = raw.getChampionLevel();
        result.championExperience = raw.getChampionExperience();
        result.championTransform = raw.getChampionTransform();

        result.damage = raw.getTotalDamageDealtToChampions();
        result.damageBuilding = raw.getDamageDealtToBuildings();
        result.healing = raw.getTotalHealsOnTeammates() + raw.getTotalDamageShieldedOnTeammates();
        result.totalDamageDealt = raw.getTotalDamageDealt();
        result.totalDamageDealtToChampions = raw.getTotalDamageDealtToChampions();
        result.physicalDamageDealtToChampions = raw.getPhysicalDamageDealtToChampions();
        result.magicDamageDealtToChampions = raw.getMagicDamageDealtToChampions();
        result.trueDamageDealtToChampions = raw.getTrueDamageDealtToChampions();
        result.damageDealtToObjectives = raw.getDamageDealtToObjectives();
        result.damageDealtToTurrets = raw.getDamageDealtToTurrets();
        result.damageSelfMitigated = raw.getDamageSelfMitigated();
        result.totalDamageTaken = raw.getTotalDamageTaken();
        result.physicalDamageTaken = raw.getPhysicalDamageTaken();
        result.magicDamageTaken = raw.getMagicDamageTaken();
        result.trueDamageTaken = raw.getTrueDamageTaken();
        result.totalHeal = raw.getTotalHeal();
        result.totalHealsOnTeammates = raw.getTotalHealsOnTeammates();
        result.totalDamageShieldedOnTeammates = raw.getTotalDamageShieldedOnTeammates();

        result.totalMinionsKilled = raw.getTotalMinionsKilled();
        result.neutralMinionsKilled = raw.getNeutralMinionsKilled();
        result.cs = result.totalMinionsKilled + result.neutralMinionsKilled;
        result.goldEarned = raw.getGoldEarned();
        result.goldSpent = raw.getGoldSpent();
        result.ward = raw.getWardsPlaced();
        result.wardKilled = raw.getWardsKilled();
        result.visionScore = raw.getVisionScore();

        result.doubles = raw.getDoubleKills();
        result.triples = raw.getTripleKills();
        result.quadruples = raw.getQuadraKills();
        result.pentas = raw.getPentaKills();
        result.largestMultiKill = raw.getLargestMultiKill();
        result.killingSprees = raw.getKillingSprees();
        result.largestKillingSpree = raw.getLargestKillingSpree();

        result.item0 = raw.getItem0();
        result.item1 = raw.getItem1();
        result.item2 = raw.getItem2();
        result.item3 = raw.getItem3();
        result.item4 = raw.getItem4();
        result.item5 = raw.getItem5();
        result.item6 = raw.getItem6();
        result.q = raw.getSpell1Casts();
        result.w = raw.getSpell2Casts();
        result.e = raw.getSpell3Casts();
        result.r = raw.getSpell4Casts();
        result.d = raw.getSummoner1Casts();
        result.f = raw.getSummoner2Casts();
        result.summonerSpell1 = raw.getSummoner1Id();
        result.summonerSpell2 = raw.getSummoner2Id();

        result.addPings(raw);
        result.addPerks(raw.getPerks());
        result.addAugment(raw.getPlayerAugment1());
        result.addAugment(raw.getPlayerAugment2());
        result.addAugment(raw.getPlayerAugment3());
        result.addAugment(raw.getPlayerAugment4());

        result.baronKills = raw.getBaronKills();
        result.dragonKills = raw.getDragonKills();
        result.inhibitorKills = raw.getInhibitorKills();
        result.inhibitorTakedowns = raw.getInhibitorTakedowns();
        result.nexusKills = raw.getNexusKills();
        result.nexusTakedowns = raw.getNexusTakedowns();
        result.turretKills = raw.getTurretKills();
        result.turretTakedowns = raw.getTurretTakedowns();
        result.objectivesStolen = raw.getObjectivesStolen();
        result.objectivesStolenAssists = raw.getObjectivesStolenAssists();

        result.eligibleForProgression = raw.isEligibleForProgression();
        result.firstBloodAssist = raw.isFirstBloodAssist();
        result.firstBloodKill = raw.isFirstBloodKill();
        result.firstTowerAssist = raw.isFirstTowerAssist();
        result.firstTowerKill = raw.isFirstTowerKill();
        result.gameEndedInEarlySurrender = raw.isGameEndedInEarlySurrender();
        result.gameEndedInSurrender = raw.isGameEndedInSurrender();
        result.teamEarlySurrendered = raw.isTeamEarlySurrendered();
        result.timePlayed = raw.getTimePlayed();
        result.timeCCingOthers = raw.getTimeCCingOthers();
        result.totalTimeCCDealt = raw.getTotalTimeCCDealt();
        result.totalTimeSpentDead = raw.getTotalTimeSpentDead();
        result.bountyLevel = raw.getBountyLevel();
        result.roleBoundItem = raw.getRoleBoundItem();
        if (raw.getChallenges() != null) result.challenges.putAll(raw.getChallenges());
        return result;
    }

    public boolean didWin() {
        return win;
    }

    public String getPuuid() {
        return puuid;
    }

    public int getParticipantId() {
        return participantId;
    }

    public String getRiotIdName() {
        return riotIdName;
    }

    public String getRiotIdTagline() {
        return riotIdTagline;
    }

    public int getChampionId() {
        return champion;
    }

    public String getChampionName() {
        return championName;
    }

    public int getChampionLevel() {
        return level;
    }

    public LaneType getLane() {
        return lane;
    }

    public LaneType getChampionSelectLane() {
        return championSelectLane;
    }

    public TeamType getTeam() {
        return team;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getTotalDamageDealtToChampions() {
        return totalDamageDealtToChampions;
    }

    public int getTotalDamageTaken() {
        return totalDamageTaken;
    }

    public int getTotalHeal() {
        return totalHeal;
    }

    public int getTotalMinionsKilled() {
        return totalMinionsKilled;
    }

    public int getNeutralMinionsKilled() {
        return neutralMinionsKilled;
    }

    public int getGoldEarned() {
        return goldEarned;
    }

    public int getVisionScore() {
        return visionScore;
    }

    public int getWardsPlaced() {
        return ward;
    }

    public int getWardsKilled() {
        return wardKilled;
    }

    public int getPlayerSubteamId() {
        return subTeam;
    }

    public int getSubteamPlacement() {
        return subTeamPlacement;
    }

    public int getPlacement() {
        return placement;
    }

    public int getTurretKills() {
        return turretKills;
    }

    public int getRoleBoundItem() {
        return roleBoundItem;
    }

    public int getItem0() {
        return item0;
    }

    public int getItem1() {
        return item1;
    }

    public int getItem2() {
        return item2;
    }

    public int getItem3() {
        return item3;
    }

    public int getItem4() {
        return item4;
    }

    public int getItem5() {
        return item5;
    }

    public int getItem6() {
        return item6;
    }

    public int getSummoner1Id() {
        return summonerSpell1;
    }

    public int getSummoner2Id() {
        return summonerSpell2;
    }

    public int getPlayerAugment1() {
        return getAugment(0);
    }

    public int getPlayerAugment2() {
        return getAugment(1);
    }

    public int getPlayerAugment3() {
        return getAugment(2);
    }

    public int getPlayerAugment4() {
        return getAugment(3);
    }

    private void addPings(MatchParticipant raw) {
        pings.put("push", raw.getPushPings());
        pings.put("bait", raw.getBaitPings());
        pings.put("danger", raw.getDangerPings());
        pings.put("hold", raw.getHoldPings());
        pings.put("all_in", raw.getAllInPings());
        pings.put("basic", raw.getBasicPings());
        pings.put("command", raw.getCommandPings());
        pings.put("get_back", raw.getGetBackPings());
        pings.put("on_my_way", raw.getOnMyWayPings());
        pings.put("assist_me", raw.getAssistMePings());
        pings.put("need_vision", raw.getNeedVisionPings());
        pings.put("enemy_vision", raw.getEnemyVisionPings());
        pings.put("enemy_missing", raw.getEnemyMissingPings());
        pings.put("vision_cleared", raw.getVisionClearedPings());
    }

    private void addPerks(MatchPerks perks) {
        if (perks == null) return;

        List<PerkStyle> styles = perks.getPerkStyles();
        if (styles != null) {
            for (int index = 0; index < styles.size(); index++) {
                PerkStyle style = styles.get(index);
                List<Integer> target = index == 0 ? primaryRunes : secondaryRunes;
                target.add(style.getStyle());
                if (style.getSelections() == null) continue;
                for (PerkSelection selection : style.getSelections()) {
                    target.add(selection.getPerk());
                }
            }
        }

        StatPerk stats = perks.getStatPerks();
        if (stats == null) return;
        statsRunes.add(stats.getDefense());
        statsRunes.add(stats.getFlex());
        statsRunes.add(stats.getOffense());
    }

    private void addAugment(int augment) {
        if (augment != 0) augments.add(augment);
    }

    private int getAugment(int index) {
        return index < augments.size() ? augments.get(index) : 0;
    }
}
