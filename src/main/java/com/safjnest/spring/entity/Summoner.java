package com.safjnest.spring.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "summoner")
public class Summoner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "riot_id")
    private String riotId;

    @Column(name = "summoner_id", nullable = false)
    private String summonerId;

    @Column(name = "account_id")
    private String accountId;

    @Column(nullable = false)
    private String puuid;

    @Column(name = "league_shard", nullable = false)
    private Integer leagueShard = 3;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private Integer tracking = 0;

    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Participant> participants;

    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Masteries> masteries;

    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rank> ranks;

    // Constructors
    public Summoner() {}

    public Summoner(String riotId, String summonerId, String accountId, String puuid, Integer leagueShard, String userId) {
        this.riotId = riotId;
        this.summonerId = summonerId;
        this.accountId = accountId;
        this.puuid = puuid;
        this.leagueShard = leagueShard;
        this.userId = userId;
    }

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

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public List<Masteries> getMasteries() {
        return masteries;
    }

    public void setMasteries(List<Masteries> masteries) {
        this.masteries = masteries;
    }

    public List<Rank> getRanks() {
        return ranks;
    }

    public void setRanks(List<Rank> ranks) {
        this.ranks = ranks;
    }
}