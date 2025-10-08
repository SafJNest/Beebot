package com.safjnest.util.lol.api.spring;

import java.time.LocalDateTime;

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
@Table(name = "masteries", schema = "league_of_legends_test")
public class MasteriesDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id")
    private SummonerDTO summoner;
    
    @Column(name = "champion_id")
    private Integer championId;
    
    @Column(name = "champion_level")
    private Integer championLevel;
    
    @Column(name = "champion_points")
    private Integer championPoints;
    
    @Column(name = "last_play_time")
    private LocalDateTime lastPlayTime;
    
    // Constructors
    public MasteriesDTO() {}
    
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
    
    public Integer getChampionId() {
        return championId;
    }
    
    public void setChampionId(Integer championId) {
        this.championId = championId;
    }
    
    public Integer getChampionLevel() {
        return championLevel;
    }
    
    public void setChampionLevel(Integer championLevel) {
        this.championLevel = championLevel;
    }
    
    public Integer getChampionPoints() {
        return championPoints;
    }
    
    public void setChampionPoints(Integer championPoints) {
        this.championPoints = championPoints;
    }
    
    public LocalDateTime getLastPlayTime() {
        return lastPlayTime;
    }
    
    public void setLastPlayTime(LocalDateTime lastPlayTime) {
        this.lastPlayTime = lastPlayTime;
    }
}