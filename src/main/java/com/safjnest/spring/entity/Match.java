package com.safjnest.spring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "`match`")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id", nullable = false, unique = true)
    private String gameId;

    @Column(name = "league_shard", nullable = false)
    private Integer leagueShard;

    @Column(name = "game_type", nullable = false)
    private Integer gameType;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String bans;

    @Column(name = "time_start", nullable = false)
    private LocalDateTime timeStart;

    @Column(name = "time_end", nullable = false)
    private LocalDateTime timeEnd;

    @Column(nullable = false)
    private String patch;

    @Column(columnDefinition = "LONGTEXT")
    private String events;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Participant> participants;

    // Constructors
    public Match() {}

    public Match(String gameId, Integer leagueShard, Integer gameType, String bans, 
                 LocalDateTime timeStart, LocalDateTime timeEnd, String patch) {
        this.gameId = gameId;
        this.leagueShard = leagueShard;
        this.gameType = gameType;
        this.bans = bans;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.patch = patch;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Integer getLeagueShard() {
        return leagueShard;
    }

    public void setLeagueShard(Integer leagueShard) {
        this.leagueShard = leagueShard;
    }

    public Integer getGameType() {
        return gameType;
    }

    public void setGameType(Integer gameType) {
        this.gameType = gameType;
    }

    public String getBans() {
        return bans;
    }

    public void setBans(String bans) {
        this.bans = bans;
    }

    public LocalDateTime getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(LocalDateTime timeStart) {
        this.timeStart = timeStart;
    }

    public LocalDateTime getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(LocalDateTime timeEnd) {
        this.timeEnd = timeEnd;
    }

    public String getPatch() {
        return patch;
    }

    public void setPatch(String patch) {
        this.patch = patch;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }
}