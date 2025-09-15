package com.safjnest.util.lol.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rank", schema = "league_of_legends")
public class RankDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne
    @JoinColumn(name = "summoner_id", nullable = false)
    private SummonerDTO summoner;
    
    @Column(name = "game_type", nullable = false)
    private Integer gameType;
    
    @Column(nullable = false)
    private Integer rank;
    
    @Column(nullable = false)
    private Integer lp;
    
    @Column(nullable = false)
    private Integer wins;
    
    @Column(nullable = false)
    private Integer losses;
    
    @Column(name = "last_update", nullable = false)
    private LocalDateTime lastUpdate;
    
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
    
    public Integer getGameType() {
        return gameType;
    }
    
    public void setGameType(Integer gameType) {
        this.gameType = gameType;
    }
    
    public Integer getRank() {
        return rank;
    }
    
    public void setRank(Integer rank) {
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