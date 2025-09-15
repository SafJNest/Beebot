package com.safjnest.util.lol.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "summoner", schema = "league_of_legends")
public class SummonerDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "riot_id")
    private String riotId;
    
    @Column(name = "summoner_id", nullable = false)
    private String summonerId;
    
    @Column(name = "account_id")
    private String accountId;
    
    @Column(name = "puuid", nullable = false)
    private String puuid;
    
    @Column(name = "league_shard", nullable = false)
    private Integer leagueShard = 3;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "tracking", nullable = false)
    private Integer tracking = 0;
    
    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL)
    private List<RankDTO> ranks;
    
    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL)
    private List<ParticipantDTO> participants;
    
    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL)
    private List<MasteryDTO> masteries;
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getRiotId() {
        return riotId;
    }
    
    public void setRiotId(String riotId) {
        this.riotId = riotId;
    }
    
    public String getSummonerId() {
        return summonerId;
    }
    
    public void setSummonerId(String summonerId) {
        this.summonerId = summonerId;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getPuuid() {
        return puuid;
    }
    
    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }
    
    public Integer getLeagueShard() {
        return leagueShard;
    }
    
    public void setLeagueShard(Integer leagueShard) {
        this.leagueShard = leagueShard;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Integer getTracking() {
        return tracking;
    }
    
    public void setTracking(Integer tracking) {
        this.tracking = tracking;
    }
    
    public List<RankDTO> getRanks() {
        return ranks;
    }
    
    public void setRanks(List<RankDTO> ranks) {
        this.ranks = ranks;
    }
    
    public List<ParticipantDTO> getParticipants() {
        return participants;
    }
    
    public void setParticipants(List<ParticipantDTO> participants) {
        this.participants = participants;
    }
    
    public List<MasteryDTO> getMasteries() {
        return masteries;
    }
    
    public void setMasteries(List<MasteryDTO> masteries) {
        this.masteries = masteries;
    }
}