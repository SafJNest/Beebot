package com.safjnest.spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guild")
@Data
@NoArgsConstructor
public class GuildEntity {

    @Id
    @Column(name = "guild_id", length = 20)
    private String guildId;

    @Column(name = "prefix", length = 10)
    private String prefix;

    @Column(name = "exp_enabled")
    private Boolean expEnabled = true;

    @Column(name = "threshold")
    private Integer threshold = 0;

    @Column(name = "blacklist_channel", length = 20)
    private String blacklistChannel;

    @Column(name = "blacklist_enabled")
    private Boolean blacklistEnabled = false;

    @Column(name = "name_tts", length = 50)
    private String nameTts;

    @Column(name = "language_tts", length = 10)
    private String languageTts;

    @Column(name = "league_shard")
    private Integer leagueShard;

    public GuildEntity(String guildId, String prefix) {
        this.guildId = guildId;
        this.prefix = prefix;
    }
}