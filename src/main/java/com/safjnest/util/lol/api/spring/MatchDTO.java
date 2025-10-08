package com.safjnest.util.lol.api.spring;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "match", schema = "league_of_legends_test")
public class MatchDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "game_id", unique = true)
    private String gameId;
    
    private String queue;
    private String region;
    private String rank;
    
    @Column(name = "time_start")
    private LocalDateTime timeStart;
    
    @Column(name = "time_end")
    private LocalDateTime timeEnd;
    
    @Column(columnDefinition = "longtext")
    private String events;
    
    @Column(columnDefinition = "longtext")
    private String bans;
    
    private String patch;
    
    @OneToMany(mappedBy = "match", fetch = FetchType.LAZY)
    private List<ParticipantDTO> participants;
    
    // Constructors
    public MatchDTO() {}
    
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
    
    public String getQueue() {
        return queue;
    }
    
    public void setQueue(String queue) {
        this.queue = queue;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getRank() {
        return rank;
    }
    
    public void setRank(String rank) {
        this.rank = rank;
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
    
    public String getEvents() {
        return events;
    }
    
    public void setEvents(String events) {
        this.events = events;
    }
    
    public String getBans() {
        return bans;
    }
    
    public void setBans(String bans) {
        this.bans = bans;
    }
    
    public String getPatch() {
        return patch;
    }
    
    public void setPatch(String patch) {
        this.patch = patch;
    }
    
    public List<ParticipantDTO> getParticipants() {
        return participants;
    }
    
    public void setParticipants(List<ParticipantDTO> participants) {
        this.participants = participants;
    }
}