package com.safjnest.bot.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.safjnest.bot.commands.settings.Voice;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class VoiceAutocompleteTest {
    @Test
    public void findsVoicesBeyondTheFirstTwentyFiveCatalogEntries() {
        List<Choice> byName = EventAutoCompleteInteractionHandler.voiceChoices(" nAnCy ");
        assertEquals(List.of("Nancy"), byName.stream().map(Choice::getAsString).toList());

        List<Choice> byLanguage = EventAutoCompleteInteractionHandler.voiceChoices("JA-JP");
        assertEquals(Set.of("Fumi", "Akira", "Airi"),
            byLanguage.stream().map(Choice::getAsString).collect(Collectors.toSet()));
    }

    @Test
    public void sendsUpToTwentyFiveSuggestionsAndRegistersVoiceAutocomplete() {
        assertEquals(25, EventAutoCompleteInteractionHandler.voiceChoices("").size());
        assertTrue(EventAutoCompleteInteractionHandler.voiceChoices("no-such-voice").isEmpty());

        SlashCommandData command = (SlashCommandData) new Voice().buildCommandData();
        assertEquals("voice", command.getOptions().getFirst().getName());
        assertTrue(command.getOptions().getFirst().isAutoComplete());
    }
}
