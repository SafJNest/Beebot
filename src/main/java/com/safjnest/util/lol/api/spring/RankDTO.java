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
@Table(name = "rank", schema = "league_of_legends_test")
public class RankDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id")
    private SummonerDTO summoner;
    
    private String queue;
    private String rank;
    private Integer lp;
    private Integer wins;
    private Integer losses;
    
    @Column(name = "last_update")
    private LocalDateTime lastUpdate;
    
    // Constructors
    public RankDTO() {}
    
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
    
    public String getQueue() {
        return queue;
    }
    
    public void setQueue(String queue) {
        this.queue = queue;
    }
    
    public String getRank() {
        return rank;
    }
    
    public void setRank(String rank) {
        this.rank = rank;
    }
    
    public Integer getLp() {
        return lp;
    }
    
    public void setLp(Integer lp) {
        this.lp = lp;
    }
    
    public Integer getWins() {
        return wins;
    }
    
    public void setWins(Integer wins) {
        this.wins = wins;
    }
    
    public Integer getLosses() {
        return losses;
    }
    
    public void setLosses(Integer losses) {
        this.losses = losses;
    }
    
    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }
    
    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}