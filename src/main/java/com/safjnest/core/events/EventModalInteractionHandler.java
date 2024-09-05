package com.safjnest.core.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.safjnest.commands.audio.CustomizeSound;
import com.safjnest.commands.misc.twitch.TwitchMenu;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

public class EventModalInteractionHandler extends ListenerAdapter {

    private static void sound(ModalInteractionEvent event) {
        String soundId = event.getModalId().split("-", 2)[1];

        EmbedBuilder eb = null;
        Sound sound = SoundHandler.getSoundById(soundId);
        String newName = event.getValue("sound-name").getAsString();
        sound.setName(newName);

        eb = CustomizeSound.getEmbed(event.getUser(), sound);
        event.editMessageEmbeds(eb.build()).setComponents(SoundHandler.getSoundButton(soundId)).queue();
    }

    private static void tag(ModalInteractionEvent event) {
        String soundId = event.getModalId().split("-", 2)[1];

        EmbedBuilder eb = null;
        Sound sound = SoundHandler.getSoundById(soundId.split("-")[0]);
        String newTagName = event.getValue("tag-name").getAsString();
        Tag tag = SoundHandler.getTagByName(newTagName);
        Tag[] tags = sound.getTags();
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].getId() == Integer.parseInt(soundId.split("-")[1])) {
                tags[i] = tag;
                break;
            }
        }
        sound.setTags(tags);

        eb = CustomizeSound.getEmbed(event.getUser(), sound);
        event.editMessageEmbeds(eb.build()).setComponents(SoundHandler.getSoundButton(soundId.split("-")[0])).queue();
    }

    private static void twitch(ModalInteractionEvent event) {
        String streamerId = event.getModalId().split("-", 2)[1];
        streamerId = streamerId.equals("0") ? TwitchClient.getStreamerByName(event.getValue("twitch-streamer").getAsString()).getId() : streamerId;

        String message = event.getValue("twitch-changeMessage") != null ? event.getValue("twitch-changeMessage").getAsString() : null;
        String channel = event.getValue("twitch-changeChannel") != null ? event.getValue("twitch-changeChannel").getAsString() : null;

        if (channel != null) channel = channel.substring(channel.lastIndexOf("/") + 1);

        DatabaseHandler.setTwitchSubscriptions(streamerId, event.getGuild().getId(), channel, message);

        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchStreamerEmbed(streamerId, event.getGuild().getId()).build())
                .setComponents(TwitchMenu.getTwitchStreamerButtons(streamerId))
                .queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId().split("-", 2)[0];
        
        switch (id) {
            case "sound":
                sound(event);
                break;

            case "tag":        
                tag(event);
                break;
            case "twitch":
                twitch(event);
                break;
            default:
                break;
        }
    }
}