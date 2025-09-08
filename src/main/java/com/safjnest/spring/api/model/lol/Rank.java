package com.safjnest.spring.api.model.lol;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rank")
public class Rank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id", nullable = false)
    private Summoner summoner;

    @Column(name = "game_type", nullable = false)
    private Integer gameType;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "lp", nullable = false)
    private Integer lp;

    @Column(name = "wins", nullable = false)
    private Integer wins;

    @Column(name = "losses", nullable = false)
    private Integer losses;

    @Column(name = "last_update", nullable = false)
    private LocalDateTime lastUpdate;

    // Constructors
    public Rank() {}

    public Rank(Summoner summoner, Integer gameType, Integer rank, Integer lp, Integer wins, Integer losses, LocalDateTime lastUpdate) {
        this.summoner = summoner;
        this.gameType = gameType;
        this.rank = rank;
        this.lp = lp;
        this.wins = wins;
        this.losses = losses;
        this.lastUpdate = lastUpdate;
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