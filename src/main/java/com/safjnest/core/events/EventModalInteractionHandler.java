package com.safjnest.core.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.commands.audio.sound.SoundCustomize;
import com.safjnest.commands.misc.twitch.TwitchMenu;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.AlertMessage;
import com.safjnest.util.SafJNest;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;
import com.safjnest.util.lol.LeagueMessageType;
import com.safjnest.util.twitch.TwitchClient;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
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
            case "alert":
                alert(event);
                break;
            case "reward":
                reward(event);
                break;
            case "champion":
                champion(event);
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

    private void alert(ModalInteractionEvent event) {
        String alertId = event.getModalId().split("-", 2)[1];
        AlertData alert = GuildCache.getGuild(event.getGuild()).getAlertByID(alertId);


        String publicMessage = event.getValue("alert-message-public") != null ? event.getValue("alert-message-public").getAsString() : null;
        String privateMessage = event.getValue("alert-message-private") != null ? event.getValue("alert-message-private").getAsString() : null;

        if (publicMessage != null) alert.setMessage(publicMessage);
        if (privateMessage != null) alert.setPrivateMessage(privateMessage);

        event.deferEdit().queue();
        event.getMessage().editMessageComponents(AlertMessage.build(GuildCache.getGuild(event.getGuild()), alert)).useComponentsV2().queue();
    }

    private void reward(ModalInteractionEvent event) {
        String parse = event.getValue("reward-level").getAsString();
        if (!parse.matches("-?\\d+(\\.\\d+)?")) {
            event.deferReply(true).setContent("Insert only a number").queue();
            return;
        }
        int level = Integer.parseInt(parse);
        if(GuildCache.getGuild(event.getGuild().getId()).getAlert(AlertType.REWARD, level) != null) {
            event.deferReply(true).setContent("A reward with this level already exists.").queue();
            return; 
        }
        RewardData reward = RewardData.createRewardData(event.getGuild().getId(), "", "", AlertSendType.CHANNEL, null, level, false);
        GuildCache.getGuild(event.getGuild().getId()).getAlerts().put(reward.getKey(), reward);
        event.deferEdit().queue();
        event.getMessage().editMessageComponents(AlertMessage.build(GuildCache.getGuild(event.getGuild()), reward)).useComponentsV2().queue();

    }



    private void champion(ModalInteractionEvent event) {
        String champoString = event.getValue("champion-change").getAsString();

        ArrayList<String> championsName = new ArrayList<>();
        for (String champion : LeagueHandler.getChampions()) {
            championsName.add(champion);
        }
        champoString = SafJNest.findSimilarWord(champoString, championsName);
        StaticChampion newChampion = LeagueHandler.getChampionByName(champoString);


        if (newChampion == null) {
            event.deferReply().setEphemeral(true).addContent("Cannot find the champion you are looking for").queue();
            return;
        }


        GameQueueType queue = null;
        LeagueMessageType type = null;
        LaneType lane = null;

        boolean showChampion = true;

        long[] time = LeagueHandler.getCurrentSplitRange();
        String timeString = "current";

        String puuid = "";
        String region = "";


        for (Button b : EventUtils.getButtons(event.getMessage().getComponents())) {
            if (b.getCustomId().startsWith("champion-center-")) {
                puuid = b.getCustomId().split("-", 3)[2].substring(0, b.getCustomId().split("-", 3)[2].indexOf("#"));
                region = b.getCustomId().split("-", 3)[2].substring(b.getCustomId().split("-", 3)[2].indexOf("#") + 1);
            }
            if (b.getCustomId().startsWith("champion-queue-") && b.getStyle() == ButtonStyle.SUCCESS) {
                queue = GameQueueType.valueOf(b.getCustomId().split("-")[2]);
            }
            if (b.getCustomId().startsWith("champion-type-") && b.getStyle() == ButtonStyle.SUCCESS)
                type = LeagueMessageType.valueOf(b.getCustomId().split("-")[2]);

            if (b.getCustomId().startsWith("champion-lane-") && b.getStyle() == ButtonStyle.SUCCESS)
                lane = LaneType.valueOf(b.getCustomId().split("-")[2]);
            
            if (b.getCustomId().startsWith("champion-season-") && b.getStyle() == ButtonStyle.SUCCESS)
                timeString = b.getCustomId().split("-")[2];

        }
        
        
        event.deferEdit().queue();
        String user_id = LeagueDB.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));
        if (EventUtils.getButtonById(event.getMessage().getComponents(), "champion-left") == null) user_id = "";
        Summoner s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
        switch (timeString) {
            case "all":
                time = new long[] {0, 0};
                break;
            case "current":
                time = LeagueHandler.getCurrentSplitRange();
                break;
            case "previous":
                time = LeagueHandler.getPreviousSplitRange();
                break;
        }
        int summonerId = LeagueDB.getSummonerIdByPuuid(s.getPUUID(), s.getPlatform());
        LeagueMessage.sendChampionMessage(event.getHook(), user_id, type, s, summonerId, newChampion, time[0], time[1], queue, lane, showChampion); 
    }
}