package com.safjnest.bot.model;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.database.sql.QueryRecord;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class CacheHydrationTest {

    @Test
    public void userUsesPreloadedAliasesWithoutOpeningStorage() {
        QueryRecord alias = new QueryRecord();
        alias.put("id", 42);
        alias.put("name", "shortcut");
        alias.put("command", "play example");
        UserData user = UserData.fromRows("123", List.of(alias));
        assertEquals("123", user.getId());
        assertEquals(1, user.getAliases().size());
        assertNotNull(user.getAliases().get("shortcut"));
    }

    @Test
    public void guildUsesPreloadedChannelsWithoutOpeningStorage() {
        QueryRecord row = new QueryRecord();
        row.put("guild_id", "123");
        row.put("prefix", "!");
        row.put("exp_enabled", true);
        row.put("league_shard", LeagueShard.EUW1.ordinal());
        row.put("name_tts", "voice");
        row.put("language_tts", "it-it");
        QueryRecord channel = new QueryRecord();
        channel.put("id", "7");
        channel.put("guild_id", "123");
        channel.put("channel_id", "456");
        channel.put("league_shard", 0);
        GuildData guild = GuildData.fromRows(row, List.of(channel), List.of());
        assertEquals("123", guild.getId());
        assertEquals("!", guild.getPrefix());
        assertEquals("voice", guild.getVoice());
        assertEquals(LeagueShard.EUW1, guild.getLeagueShard());
        assertEquals(1, guild.getChannels().size());
        assertEquals("456", guild.getChannels().get("456").getRoomId());
    }
}
