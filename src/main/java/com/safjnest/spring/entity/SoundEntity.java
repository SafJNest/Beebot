package com.safjnest.spring.entity;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sound")
@Data
@NoArgsConstructor
public class SoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "guild_id", length = 20, nullable = false)
    private String guildId;

    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Column(name = "extension", length = 10)
    private String extension;

    @Column(name = "public")
    private Boolean isPublic = false;

    @Column(name = "time")
    private Timestamp time;

    @Column(name = "plays")
    private Integer plays = 0;

    @Column(name = "likes")
    private Integer likes = 0;

    @Column(name = "dislikes")
    private Integer dislikes = 0;

    public SoundEntity(String name, String guildId, String userId, String extension, Boolean isPublic) {
        this.name = name;
        this.guildId = guildId;
        this.userId = userId;
        this.extension = extension;
        this.isPublic = isPublic;
        this.time = Timestamp.from(Instant.now());
    }
}