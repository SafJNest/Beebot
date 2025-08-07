package com.safjnest.core.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

import com.safjnest.commands.audio.sound.SoundCustomize;
import com.safjnest.commands.misc.twitch.TwitchMenu;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import com.safjnest.core.cache.managers.GuildCache;

public class EventModalInteractionHandler extends ListenerAdapter {

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
            case "greet":
                greet(event);
                break;
            default:
                break;
        }
    }

    private void greet(ModalInteractionEvent event) {
        String type = event.getModalId().split("-", 2)[1];
        String input = event.getValue("greet-set").getAsString();

        Sound sound = SoundCache.getSoundByString(input, event.getGuild(), event.getUser());
        if (sound == null) {
            event.deferReply(true).setContent("Sound not found. Use command /list or /search sound").queue();
            return;
        }
        if (type.equals("global"))
            UserCache.getUser(event.getUser().getId()).setGreet("0", sound.getId());
        else 
            UserCache.getUser(event.getUser().getId()).setGreet(event.getGuild().getId(), sound.getId());
        
        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(SoundEmbed.getGreetViewEmbed(event.getUser().getId(), event.getGuild().getId()).build())
                .setComponents(SoundEmbed.getGreetButton(event.getUser().getId(), event.getGuild().getId())).queue();
    }

    private static void sound(ModalInteractionEvent event) {
        String soundId = event.getModalId().split("-", 2)[1];

        EmbedBuilder eb = null;
        Sound sound = SoundCache.getSoundById(soundId);
        String newName = event.getValue("sound-name").getAsString();
        sound.setName(newName);

        eb = SoundCustomize.getEmbed(event.getUser(), sound);
        event.editMessageEmbeds(eb.build()).setComponents(SoundEmbed.getSoundButton(soundId)).queue();
    }

    private static void tag(ModalInteractionEvent event) {
        String soundId = event.getModalId().split("-", 2)[1];

        EmbedBuilder eb = null;
        Sound sound = SoundCache.getSoundById(soundId.split("-")[0]);
        String newTagName = event.getValue("tag-name").getAsString();
        Tag tag = SoundCache.getTagByName(newTagName);
        List<Tag> tags = sound.getTags();
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getId() == Integer.parseInt(soundId.split("-")[1])) {
                tags.set(i, tag);
                break;
            }
        }
        sound.setTags(tags);

        eb = SoundCustomize.getEmbed(event.getUser(), sound);
        event.editMessageEmbeds(eb.build()).setComponents(SoundEmbed.getSoundButton(soundId.split("-")[0])).queue();
    }

    private static void twitch(ModalInteractionEvent event) {
        event.deferEdit().queue();
        Guild guild = event.getGuild();

        String streamerId = event.getModalId().split("-", 2)[1];
        streamerId = streamerId.equals("0") ? TwitchClient.getStreamerByName(event.getValue("twitch-streamer").getAsString()).getId() : streamerId;

        String message = event.getValue("twitch-changeMessage") != null ? event.getValue("twitch-changeMessage").getAsString() : null;
        String privateMessage = event.getValue("twitch-changePrivateMessage") != null ? event.getValue("twitch-changePrivateMessage").getAsString() : null;
        
        String channel = event.getValue("twitch-changeChannel") != null ? event.getValue("twitch-changeChannel").getAsString() : null;
        if (channel != null) channel = channel.substring(channel.lastIndexOf("/") + 1);

        String roleID = event.getValue("twitch-changeRole") != null ? event.getValue("twitch-changeRole").getAsString() : null;
        
        if (roleID != null && !roleID.isBlank()) {
            Role role = null;
        
            List<Role> rolesByName = guild.getRolesByName(roleID, true);
            if (!rolesByName.isEmpty()) 
                role = rolesByName.get(0);
            else if (roleID.matches("\\d+")) 
                role = guild.getRoleById(roleID);
            
            roleID = (role != null) ? role.getId() : null;
        } else {
            roleID = null;
        }

        TwitchData twitch = GuildCache.getGuildOrPut(guild).getTwitchdata(streamerId);
        if (twitch == null) {
            AlertSendType sendType = (privateMessage != null && !privateMessage.isBlank()) ? AlertSendType.BOTH : AlertSendType.CHANNEL;
            
            roleID = roleID.isBlank() ? null : roleID;
            
            TwitchData newTwitchData = TwitchData.createTwitchData(event.getGuild().getId(), streamerId, message, privateMessage, channel, sendType, roleID);

            if(newTwitchData.getID() == 0) {
                event.deferReply(true).addContent("Something went wrong.").queue();
                return;
            }
    
            GuildCache.getGuildOrPut(event.getGuild().getId()).getAlerts().put(newTwitchData.getKey(), newTwitchData);
            TwitchClient.registerSubEvent(streamerId);
            event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchStreamerEmbed(streamerId, event.getGuild().getId()).build())
                .setComponents(TwitchMenu.getTwitchStreamerButtons(streamerId))
                .queue();
            return;
        }

        if (message != null) twitch.setMessage(message);
        if (privateMessage != null && !privateMessage.isBlank()) {
            twitch.setPrivateMessage(privateMessage);
            twitch.setSendType(AlertSendType.BOTH);
        }
        if (channel != null) twitch.setAlertChannel(channel);
        if (roleID != null) twitch.setStreamerRole(roleID);

        event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchStreamerEmbed(streamerId, event.getGuild().getId()).build())
                .setComponents(TwitchMenu.getTwitchStreamerButtons(streamerId))
                .queue();
    }
}