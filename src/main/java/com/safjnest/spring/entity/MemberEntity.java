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
@Table(name = "member")
@Data
@NoArgsConstructor
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", length = 20, nullable = false)
    private String guildId;

    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Column(name = "experience")
    private Integer experience = 0;

    @Column(name = "level")
    private Integer level = 0;

    @Column(name = "messages")
    private Integer messages = 0;

    @Column(name = "update_time")
    private Integer updateTime = 0;

    public MemberEntity(String guildId, String userId) {
        this.guildId = guildId;
        this.userId = userId;
    }
}