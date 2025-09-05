package com.safjnest.spring.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sound")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sound {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "guild_id", length = 19, nullable = false)
    private String guildId;
    
    @Column(name = "user_id", length = 19, nullable = false)
    private String userId;
    
    @Column(name = "extension", length = 4, nullable = false)
    private String extension;
    
    @Column(name = "public")
    private Boolean isPublic = false;
    
    @Column(name = "time")
    private LocalDateTime time;
    
    @Column(name = "plays")
    private Integer plays = 0;
    
    @Column(name = "likes")
    private Integer likes = 0;
    
    @Column(name = "dislikes")
    private Integer dislikes = 0;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", referencedColumnName = "guild_id", insertable = false, updatable = false)
    private Guild guild;
}