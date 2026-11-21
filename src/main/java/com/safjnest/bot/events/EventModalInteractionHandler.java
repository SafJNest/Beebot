package com.safjnest.bot.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import com.safjnest.lol.model.summoner.Summoner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.safjnest.bot.audio.SoundEmbed;
import com.safjnest.bot.commands.audio.sound.SoundCustomize;
import com.safjnest.bot.commands.members.Blacklist;
import com.safjnest.bot.commands.misc.twitch.TwitchMenu;
import com.safjnest.bot.model.guild.ChannelData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertSendType;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.UserCache;
import com.safjnest.lol.message.LeagueMessage;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.AlertMessage;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.twitch.TwitchClient;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.utils.ChampionUtils;

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
            case "blacklist":
                blacklist(event);
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
            UserCache.withUser(event.getUser().getId(), user -> user.setGreet("0", sound.getId()));
        else 
            UserCache.withUser(event.getUser().getId(), user -> user.setGreet(event.getGuild().getId(), sound.getId()));
        
        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(SoundEmbed.getGreetViewEmbed(event.getUser().getId(), event.getGuild().getId()).build())
                .setComponents(SoundEmbed.getGreetButton(event.getUser().getId(), event.getGuild().getId())).queue();
    }

    private static void sound(ModalInteractionEvent event) {
        String id = event.getModalId().split("-", 2)[1];
        String name = event.getValue("sound-name").getAsString().trim();
        if (name.isEmpty() || name.length() > 100 || name.matches("[0-9]+")) {
            event.reply("Invalid sound name.").setEphemeral(true).queue();
            return;
        }

        SoundCustomize.saveEdit(event, id, draft -> draft.setName(name));
    }

    private static void tag(ModalInteractionEvent event) {
        String[] ids = event.getModalId().split("-", 3);
        String name = event.getValue("tag-name").getAsString().trim().toLowerCase(Locale.ROOT);
        if (name.length() > 255) {
            event.reply("Invalid sound tag.").setEphemeral(true).queue();
            return;
        }

        int tagId = Integer.parseInt(ids[2]);
        SoundCustomize.saveEdit(event, ids[1], draft -> {
            List<Tag> tags = draft.getTags();
            for (int index = 0; index < tags.size(); index++) {
                if (tags.get(index).getId() == tagId) {
                    tags.set(index, new Tag(0, name));

                    return draft.setTags(tags);
                }
            }

            return false;
        });
    }

    private static void twitch(ModalInteractionEvent event) {
        event.deferEdit().queue();
        Guild guild = event.getGuild();

        String streamerId = event.getModalId().split("-", 2)[1];
        streamerId = streamerId.equals("0") ? TwitchClient.getStreamerByName(event.getValue("twitch-streamer").getAsString()).getId() : streamerId;

        String message = event.getValue("twitch-changeMessage") != null ? event.getValue("twitch-changeMessage").getAsString() : null;
        String privateMessage = event.getValue("twitch-changePrivateMessage") != null ? event.getValue("twitch-changePrivateMessage").getAsString() : null;
        
        String channel = event.getValue("twitch-changeChannel") != null ? event.getValue("twitch-changeChannel").getAsString() : null;
        if (channel != null)
            channel = channel.substring(channel.lastIndexOf("/") + 1);

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

        String selectedStreamerId = streamerId;
        String selectedChannelId = channel;
        String selectedRoleId = roleID;

        Boolean created = GuildCache.withGuild(guild.getId(), cached -> {
            TwitchData current = cached.getTwitchdata(selectedStreamerId);
            TwitchData edit = current == null
                ? (TwitchData) cached.newAlert(AlertType.TWITCH, selectedStreamerId)
                : current.deferEdit();

            if (message != null)
                edit.setMessage(message);

            if (privateMessage != null && !privateMessage.isBlank()) {
                edit.setPrivateMessage(privateMessage);
                edit.setSendType(AlertSendType.BOTH);
            }

            if (selectedChannelId != null)
                edit.setAlertChannel(selectedChannelId);

            if (selectedRoleId != null)
                edit.setStreamerRole(selectedRoleId);

            if (!edit.update())
                return null;

            if (current == null)
                cached.addAlert(edit);

            return current == null;
        });

        if (created == null) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        if (created)
            TwitchClient.registerSubEvent(streamerId);

        event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchStreamerEmbed(streamerId, event.getGuild().getId()).build())
                .setComponents(TwitchMenu.getTwitchStreamerButtons(streamerId))
                .queue();
    }

    private void alert(ModalInteractionEvent event) {
        String alertId = event.getModalId().split("-", 2)[1];
        AlertData alert = GuildCache.getGuild(event.getGuild()).getAlertByID(alertId);
        ChannelData channelData = null;
        for (EntitySelectMenu menu : EventUtils.getChannelMenu(event.getMessage().getComponents())) {
          if (menu.getCustomId().startsWith("alert-levelchannel-") && menu.getDefaultValues().size() > 0) {
            channelData = GuildCache.getGuild(event.getGuild()).getChannelData(menu.getDefaultValues().get(0).getId());
          }
        }


        String publicMessage = event.getValue("alert-message-public") != null ? event.getValue("alert-message-public").getAsString() : null;
        String privateMessage = event.getValue("alert-message-private") != null ? event.getValue("alert-message-private").getAsString() : null;

        String selectedChannelId = channelData == null ? null : channelData.getRoomId();
        GuildCache.withGuild(event.getGuild().getId(), guild -> {
            AlertData current = guild.getAlertByID(alertId);

            if (publicMessage != null || privateMessage != null) {
                AlertData edit = current.deferEdit();

                if (publicMessage != null)
                    edit.setMessage(publicMessage);

                if (privateMessage != null)
                    edit.setPrivateMessage(privateMessage);

                edit.update();
            }

            if (current.getType() == AlertType.LEVEL_UP && selectedChannelId != null && event.getValue("alert-modifier") != null) {
                try {
                    double modifier = Double.parseDouble(event.getValue("alert-modifier").getAsString());
                    guild.getChannelData(selectedChannelId).setExperienceModifier(modifier);
                } catch (NumberFormatException ignore) {
                }
            }

            return null;
        });

        event.deferEdit().queue();
        event.getMessage().editMessageComponents(AlertMessage.build(GuildCache.getGuild(event.getGuild()), alert, channelData)).useComponentsV2().queue();
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

        if (reward == null) {
            event.deferReply(true).setContent("Something went wrong.").queue();
            return;
        }

        event.deferEdit().queue();
        event.getMessage().editMessageComponents(AlertMessage.build(GuildCache.getGuild(event.getGuild()), reward)).useComponentsV2().queue();

    }



    private void champion(ModalInteractionEvent event) {
        String champoString = event.getValue("champion-change").getAsString();

        ArrayList<String> championsName = new ArrayList<>();
        for (String champion : ChampionUtils.getChampionsNames()) {
            championsName.add(champion);
        }
        champoString = SafJNest.findSimilarWord(champoString, championsName);
        StaticChampion newChampion = ChampionUtils.getChampion(champoString);


        if (newChampion == null) {
            event.deferReply().setEphemeral(true).addContent("Cannot find the champion you are looking for").queue();
            return;
        }

        String puuid = "";
        String region = "";


        for (Button b : EventUtils.getButtons(event.getMessage().getComponents())) {
            if (b.getCustomId().startsWith(LeagueMessage.BUTTON_ID_PREFIX + "-center-")) {
                puuid = b.getCustomId().split("-", 3)[2].substring(0, b.getCustomId().split("-", 3)[2].indexOf("#"));
                region = b.getCustomId().split("-", 3)[2].substring(b.getCustomId().split("-", 3)[2].indexOf("#") + 1);
            }
        }

        LeagueMessageParameter parameter = new LeagueMessageParameter(EventUtils.getButtons(event.getMessage().getComponents()));
        parameter.setChampion(newChampion);
        parameter.setShowChampion(true);
        
        
        event.deferEdit().queue();
        String user_id = SummonerService.getUserId(puuid, LeagueShard.valueOf(region));
        if (EventUtils.getButtonById(event.getMessage().getComponents(), LeagueMessage.BUTTON_ID_PREFIX + "-left") == null) user_id = "";
        Summoner s = SummonerService.get(puuid, LeagueShard.valueOf(region));

        LeagueMessage.send(event.getHook(), user_id, s, s.puuid(), parameter);
    }

    private void blacklist(ModalInteractionEvent event) {
        try {
            int threshold = Integer.parseInt(event.getValue("blacklist-threshold").getAsString());
            GuildCache.withGuild(event.getGuild().getId(), guild -> guild.setThreshold(threshold));
        } catch (Exception e) {
            event.deferReply(true).setContent("Insert a valid number").queue();
            return;
        }

        event.deferEdit().queue();
        event.getMessage().editMessageComponents(Blacklist.getMessage(GuildCache.getGuild(event.getGuild()))).useComponentsV2().queue();
    }
}
