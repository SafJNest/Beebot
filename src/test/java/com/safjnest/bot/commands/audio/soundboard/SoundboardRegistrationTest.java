package com.safjnest.bot.commands.audio.soundboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class SoundboardRegistrationTest {
    @Test
    public void buildsCreateWithNameThumbnailAndTwentyThreeSounds() {
        SlashCommandData command = (SlashCommandData) new Soundboard().buildCommandData();
        SubcommandData create = command.getSubcommands().stream()
            .filter(subcommand -> subcommand.getName().equals("create"))
            .findFirst().orElseThrow();

        assertEquals(CommandData.MAX_OPTIONS, create.getOptions().size());
        assertEquals("name", create.getOptions().get(0).getName());
        assertEquals("thumbnail", create.getOptions().get(1).getName());
        assertEquals("sound-23", create.getOptions().getLast().getName());
        assertTrue(create.getOptions().getLast().isAutoComplete());
    }
}
