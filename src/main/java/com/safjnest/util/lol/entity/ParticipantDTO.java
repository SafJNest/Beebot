package com.safjnest.util.lol.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "participant")
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
    
    // Getters and Setters (omitted for brevity)
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    // Add remaining getters and setters here
}