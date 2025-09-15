package com.safjnest.util.lol.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "match")
public class MatchDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "game_id", nullable = false, unique = true)
    private String gameId;
    
    @Column(name = "league_shard", nullable = false)
    private Integer leagueShard;
    
    @Column(name = "game_type", nullable = false)
    private Integer gameType;
    
    @Column(columnDefinition = "longtext", nullable = false)
    private String bans;
    
    @Column(name = "time_start", nullable = false)
    private LocalDateTime timeStart;
    
    @Column(name = "time_end", nullable = false)
    private LocalDateTime timeEnd;
    
    @Column(nullable = false)
    private String patch;
    
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL)
    private List<ParticipantDTO> participants;
    
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
    
    public List<ParticipantDTO> getParticipants() {
        return participants;
    }
    
    public void setParticipants(List<ParticipantDTO> participants) {
        this.participants = participants;
    }
}