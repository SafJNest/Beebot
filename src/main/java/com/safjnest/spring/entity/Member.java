package com.safjnest.spring.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Member {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "user_id", length = 19, nullable = false)
    private String userId;
    
    @Column(name = "guild_id", length = 19, nullable = false)
    private String guildId;
    
    @Column(name = "experience")
    private Integer experience = 0;
    
    @Column(name = "level")
    private Integer level = 0;
    
    @Column(name = "messages")
    private Integer messages = 0;
    
    @Column(name = "update_time")
    private Integer updateTime;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", referencedColumnName = "guild_id", insertable = false, updatable = false)
    private Guild guild;
}