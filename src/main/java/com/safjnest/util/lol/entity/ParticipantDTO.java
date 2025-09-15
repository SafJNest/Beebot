package com.safjnest.util.lol.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "participant", schema = "league_of_legends")
public class ParticipantDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne
    @JoinColumn(name = "summoner_id", nullable = false)
    private SummonerDTO summoner;
    
    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private MatchDTO match;
    
    @Column(nullable = false)
    private Boolean win;
    
    @Column(nullable = false)
    private String kda = "";
    
    @Column
    private Short champion;
    
    @Column
    private Byte lane;
    
    @Column
    private Byte team;
    
    @Column(name = "subteam", nullable = false)
    private Byte subteam = 0;
    
    @Column(name = "subteam_placement", nullable = false)
    private Byte subteamPlacement = 0;
    
    @Column
    private Short rank;
    
    @Column
    private Short lp;
    
    @Column
    private Short gain;
    
    @Column(nullable = false)
    private Integer damage = 0;
    
    @Column(name = "damage_building", nullable = false)
    private Integer damageBuilding = 0;
    
    @Column(nullable = false)
    private Integer healing = 0;
    
    @Column(nullable = false)
    private Short cs = 0;
    
    @Column(name = "gold_earned")
    private Integer goldEarned;
    
    @Column
    private Short ward;
    
    @Column(name = "ward_killed", nullable = false)
    private Short wardKilled;
    
    @Column(name = "vision_score")
    private Short visionScore;
    
    @Column(columnDefinition = "longtext", nullable = false)
    private String pings = "{}";
    
    @Column(columnDefinition = "longtext", nullable = false)
    private String build;
    
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
    
    public Boolean getWin() {
        return win;
    }
    
    public void setWin(Boolean win) {
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
    
    public Byte getLane() {
        return lane;
    }
    
    public void setLane(Byte lane) {
        this.lane = lane;
    }
    
    public Byte getTeam() {
        return team;
    }
    
    public void setTeam(Byte team) {
        this.team = team;
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
    
    public Short getRank() {
        return rank;
    }
    
    public void setRank(Short rank) {
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
}