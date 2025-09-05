package com.safjnest.spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "soundboard_sounds")
@Data
@NoArgsConstructor
public class SoundboardSoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soundboard_id", nullable = false)
    private Long soundboardId;

    @Column(name = "sound_id", nullable = false)
    private Long soundId;

    public SoundboardSoundEntity(Long soundboardId, Long soundId) {
        this.soundboardId = soundboardId;
        this.soundId = soundId;
    }
}