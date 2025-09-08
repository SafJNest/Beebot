package com.safjnest.spring.api.model.lol;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "masteries")
public class Masteries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id", nullable = false)
    private Summoner summoner;

    @Column(name = "champion_id", nullable = false)
    private Integer championId;

    @Column(name = "champion_level", nullable = false)
    private Integer championLevel;

    @Column(name = "champion_points", nullable = false)
    private Integer championPoints;

    @Column(name = "last_play_time", nullable = false)
    private LocalDateTime lastPlayTime;

    // Constructors
    public Masteries() {}

    public Masteries(Summoner summoner, Integer championId, Integer championLevel, Integer championPoints, LocalDateTime lastPlayTime) {
        this.summoner = summoner;
        this.championId = championId;
        this.championLevel = championLevel;
        this.championPoints = championPoints;
        this.lastPlayTime = lastPlayTime;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Summoner getSummoner() {
        return summoner;
    }

    public void setSummoner(Summoner summoner) {
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