package com.safjnest.bot.commands.audio.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class SoundRegistrationTest {
    @Test
    public void buildsOverlapWithARequiredBoolean() {
        SlashCommandData command = (SlashCommandData) new Sound().buildCommandData();
        SubcommandData overlap = command.getSubcommands().stream()
            .filter(subcommand -> subcommand.getName().equals("overlap"))
            .findFirst().orElseThrow();

        assertEquals(1, overlap.getOptions().size());

        OptionData enabled = overlap.getOptions().getFirst();
        assertEquals("enabled", enabled.getName());
        assertEquals(OptionType.BOOLEAN, enabled.getType());
        assertEquals("Whether sound effects can overlap", enabled.getDescription());
        assertTrue(enabled.isRequired());
    }
}
