package com.safjnest.util.lol.api.spring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "participant")
public class ParticipantDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id")
    private SummonerDTO summoner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchDTO match;
    
    private boolean win;
    
    private String kda;
    private int kills;
    private int deaths;
    private int assists;

    private Short champion;
    private String team;
    private String lane;
    
    @Column(name = "subteam")
    private Byte subteam;
    
    @Column(name = "subteam_placement")
    private Byte subteamPlacement;
    
    private String rank;
    private Short lp;
    private Short gain;
    private Integer damage;
    
    @Column(name = "damage_building")
    private Integer damageBuilding;
    
    private Integer healing;
    private Short cs;
    
    @Column(name = "gold_earned")
    private Integer goldEarned;
    
    private Short ward;
    
    @Column(name = "ward_killed")
    private Short wardKilled;
    
    @Column(name = "vision_score")
    private Short visionScore;
    
    @Column(name = "pings", columnDefinition = "longtext")
    private String pings;
    
    @Column(name = "build", columnDefinition = "longtext")
    private String build;
    
    // Constructors
    public ParticipantDTO() {}
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public SummonerDTO getSummoner() {
        return summoner;
    }
    
    public void setSummoner(SummonerDTO summoner) {
        this.summoner = summoner;
    }
    
    public MatchDTO getMatch() {
        return match;
    }
    
    public void setMatch(MatchDTO match) {
        this.match = match;
    }
    
    public boolean isWin() {
        return win;
    }
    
    public void setWin(boolean win) {
        this.win = win;
    }
    
    public String getKda() {
        return kda;
    }
    
    public void setKda(String kda) {
        this.kda = kda;
    }
    
    public Short getChampion() {
        return champion;
    }
    
    public void setChampion(Short champion) {
        this.champion = champion;
    }
    
    public String getTeam() {
        return team;
    }
    
    public void setTeam(String team) {
        this.team = team;
    }
    
    public String getLane() {
        return lane;
    }
    
    public void setLane(String lane) {
        this.lane = lane;
    }
    
    public Byte getSubteam() {
        return subteam;
    }
    
    public void setSubteam(Byte subteam) {
        this.subteam = subteam;
    }
    
    public Byte getSubteamPlacement() {
        return subteamPlacement;
    }
    
    public void setSubteamPlacement(Byte subteamPlacement) {
        this.subteamPlacement = subteamPlacement;
    }
    
    public String getRank() {
        return rank;
    }
    
    public void setRank(String rank) {
        this.rank = rank;
    }
    
    public Short getLp() {
        return lp;
    }
    
    public void setLp(Short lp) {
        this.lp = lp;
    }
    
    public Short getGain() {
        return gain;
    }
    
    public void setGain(Short gain) {
        this.gain = gain;
    }
    
    public Integer getDamage() {
        return damage;
    }
    
    public void setDamage(Integer damage) {
        this.damage = damage;
    }
    
    public Integer getDamageBuilding() {
        return damageBuilding;
    }
    
    public void setDamageBuilding(Integer damageBuilding) {
        this.damageBuilding = damageBuilding;
    }
    
    public Integer getHealing() {
        return healing;
    }
    
    public void setHealing(Integer healing) {
        this.healing = healing;
    }
    
    public Short getCs() {
        return cs;
    }
    
    public void setCs(Short cs) {
        this.cs = cs;
    }
    
    public Integer getGoldEarned() {
        return goldEarned;
    }
    
    public void setGoldEarned(Integer goldEarned) {
        this.goldEarned = goldEarned;
    }
    
    public Short getWard() {
        return ward;
    }
    
    public void setWard(Short ward) {
        this.ward = ward;
    }
    
    public Short getWardKilled() {
        return wardKilled;
    }
    
    public void setWardKilled(Short wardKilled) {
        this.wardKilled = wardKilled;
    }
    
    public Short getVisionScore() {
        return visionScore;
    }
    
    public void setVisionScore(Short visionScore) {
        this.visionScore = visionScore;
    }
    
    public String getPings() {
        return pings;
    }
    
    public void setPings(String pings) {
        this.pings = pings;
    }
    
    public String getBuild() {
        return build;
    }
    
    public void setBuild(String build) {
        this.build = build;
    }

    public int getKills() {
        return this.kda != null && this.kda.contains("/") ? Integer.parseInt(this.kda.split("/")[0]) : 0;
    }
    public int getDeaths() {
        return this.kda != null && this.kda.contains("/") ? Integer.parseInt(this.kda.split("/")[1]) : 0;
    }

    public int getAssists() {
        return this.kda != null && this.kda.contains("/") ? Integer.parseInt(this.kda.split("/")[2]) : 0;
    }
}