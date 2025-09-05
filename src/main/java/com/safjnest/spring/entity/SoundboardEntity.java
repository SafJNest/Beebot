package com.safjnest.spring.entity;

import java.sql.Blob;
import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "soundboard")
@Data
@NoArgsConstructor
public class SoundboardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "guild_id", length = 20, nullable = false)
    private String guildId;

    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Lob
    @Column(name = "thumbnail")
    private Blob thumbnail;

    @Column(name = "created_at")
    private Timestamp createdAt;

    public SoundboardEntity(String name, String guildId, String userId) {
        this.name = name;
        this.guildId = guildId;
        this.userId = userId;
        this.createdAt = Timestamp.from(Instant.now());
    }
}