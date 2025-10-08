package com.safjnest.util.lol.api.spring;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "summoner", schema = "league_of_legends_test")
public class SummonerDTO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "riot_id")
    private String riotId;
    
    private Integer level;
    private Integer icon;
    private String region;
    private String puuid;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "ban", columnDefinition = "TINYINT(4)")
    private boolean ban;
    @Column(name = "tracking", columnDefinition = "INT(11)")
    private boolean tracking;
    
    @OneToMany(mappedBy = "summoner", fetch = FetchType.LAZY)
    private List<MasteriesDTO> masteries;
    
    @OneToMany(mappedBy = "summoner", fetch = FetchType.LAZY)
    private List<RankDTO> ranks;
    
    @OneToMany(mappedBy = "summoner", fetch = FetchType.LAZY)
    private List<ParticipantDTO> participants;
    
    // Constructors
    public SummonerDTO() {}
    
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
    
    public Integer getLevel() {
        return level;
    }
    
    public void setLevel(Integer level) {
        this.level = level;
    }
    
    public Integer getIcon() {
        return icon;
    }
    
    public void setIcon(Integer icon) {
        this.icon = icon;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getPuuid() {
        return puuid;
    }
    
    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public boolean getBan() {
        return ban;
    }
    
    public void setBan(boolean ban) {
        this.ban = ban;
    }
    
    public boolean getTracking() {
        return tracking;
    }
    
    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }
    
    public List<MasteriesDTO> getMasteries() {
        return masteries;
    }
    
    public void setMasteries(List<MasteriesDTO> masteries) {
        this.masteries = masteries;
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
}