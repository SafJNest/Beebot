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
@Table(name = "tag_sounds")
@Data
@NoArgsConstructor
public class TagSoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "sound_id", nullable = false)
    private Long soundId;

    public TagSoundEntity(Long tagId, Long soundId) {
        this.tagId = tagId;
        this.soundId = soundId;
    }
}