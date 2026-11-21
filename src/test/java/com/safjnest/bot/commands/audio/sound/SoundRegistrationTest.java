package com.safjnest.bot.commands.audio.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

import com.safjnest.bot.audio.AudioEffects.Effect;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class SoundRegistrationTest {
    @Test
    public void buildsEffectPresetsAndReset() {
        SlashCommandData command = (SlashCommandData) new Sound().buildCommandData();
        SubcommandData effect = command.getSubcommands().stream()
            .filter(subcommand -> subcommand.getName().equals("effect"))
            .findFirst().orElseThrow();
        SubcommandData reset = command.getSubcommands().stream()
            .filter(subcommand -> subcommand.getName().equals("reset"))
            .findFirst().orElseThrow();

        assertEquals(1, effect.getOptions().size());
        OptionData preset = effect.getOptions().getFirst();
        assertEquals("preset", preset.getName());
        assertTrue(preset.isRequired());

        EnumSet<Effect> offered = EnumSet.noneOf(Effect.class);
        for (Choice choice : preset.getChoices())
            offered.add(Effect.valueOf(choice.getAsString()));

        assertEquals(EnumSet.complementOf(EnumSet.of(Effect.NONE)), offered);
        assertTrue(reset.getOptions().isEmpty());
    }

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
