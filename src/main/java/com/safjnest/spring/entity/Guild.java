package com.safjnest.spring.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "guild")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Guild {
    
    @Id
    @Column(name = "guild_id", length = 19)
    private String guildId;
    
    @Column(name = "prefix", length = 32, nullable = false)
    private String prefix = "!";
    
    @Column(name = "name_tts", length = 19)
    private String nameTts;
    
    @Column(name = "language_tts", length = 19)
    private String languageTts;
    
    @Column(name = "exp_enabled", nullable = false)
    private Boolean expEnabled = true;
    
    @Column(name = "threshold")
    private Byte threshold = 0;
    
    @Column(name = "blacklist_channel", length = 19)
    private String blacklistChannel;
    
    @Column(name = "blacklist_enabled", nullable = false)
    private Boolean blacklistEnabled = false;
    
    @Column(name = "league_shard", nullable = false)
    private Byte leagueShard = 3;
}