package com.safjnest.spring.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "summoner")
@Data
@NoArgsConstructor
public class SummonerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 20)
    private String userId;

    @Column(name = "summoner_id", length = 100)
    private String summonerId;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "puuid", length = 100, unique = true)
    private String puuid;

    @Column(name = "riot_id", length = 100)
    private String riotId;

    @Column(name = "league_shard")
    private Integer leagueShard;

    @Column(name = "tracking")
    private Boolean tracking = false;

    @Column(name = "created_at")
    private Timestamp createdAt;

    public SummonerEntity(String userId, String summonerId, String accountId, String puuid, String riotId, Integer leagueShard) {
        this.userId = userId;
        this.summonerId = summonerId;
        this.accountId = accountId;
        this.puuid = puuid;
        this.riotId = riotId;
        this.leagueShard = leagueShard;
        this.createdAt = Timestamp.from(java.time.Instant.now());
    }
}