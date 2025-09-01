package com.safjnest.core.events;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.MatchTracker;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;
import com.safjnest.util.lol.LeagueMessageType;
import com.safjnest.util.twitch.TwitchClient;
import com.safjnest.commands.audio.playlist.PlaylistView;
import com.safjnest.commands.audio.sound.SoundCustomize;
import com.safjnest.commands.misc.Help;
import com.safjnest.commands.misc.twitch.TwitchMenu;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.core.audio.TrackData;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.core.audio.types.EmbedType;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.core.chat.ChatHandler;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import com.safjnest.core.cache.managers.GuildCache;


public class EventButtonHandler extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getMessage().isUsingComponentsV2()) return;
        String buttonId = event.getButton().getCustomId();

        if (buttonId.startsWith("sound-")) {
            sound(event);
            return;
        }

        else if (buttonId.startsWith("tag")) {
            tag(event);
            return;
        }

        else if (buttonId.startsWith("twitch")) {
            twitch(event);
            return;
        }

        else if (buttonId.startsWith("greet")) {
            greet(event);
            return;
        }

        else if (buttonId.startsWith("champion-")) {
            champion(event);
            return;
        }


        event.deferEdit().queue();

        /* like this
        switch (buttonId.substring(0, buttonId.indexOf("-"))) {
            case "lol":
                lolButtonEvent(event);
                break;

            default:
                break;
        }
        */

        if (buttonId.startsWith("lol-"))
            lolButtonEvent(event);

        else if (buttonId.startsWith("match-"))
            matchButtonEvent(event);

        else if (buttonId.startsWith("rank-"))
            rankButtonEvent(event);

        else if (buttonId.startsWith("list-"))
            listButtonEvent(event);

        else if (buttonId.startsWith("listuser-"))
            listUserButtonEvent(event);

        else if(buttonId.startsWith("ban-"))
            banUserEvent(event);

        else if(buttonId.startsWith("kick-"))
            kickUserEvent(event);

        else if(buttonId.startsWith("ignore-"))
            ignoreUserEvent(event);

        else if(buttonId.startsWith("unban-"))
            pardonUserEvent(event);

        else if(buttonId.startsWith("queue-"))
            queue(event);

        else if (buttonId.startsWith("reward-"))
            reward(event);

        else if (buttonId.startsWith("player-"))
            player(event);

        else if (buttonId.startsWith("soundplay-"))
            soundplay(event);

        else if (buttonId.startsWith("help"))
            help(event);

        else if (buttonId.startsWith("playlist"))
            playlist(event);

        else if (buttonId.startsWith("chat-"))
            chat(event);
    }

    private void greet(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];
        Button clicked = event.getButton();


        // if (!soundData.getUserId().equals(event.getUser().getCustomId())) {
        //     event.deferReply(true).addContent("You can only modify your own sounds").queue();
        //     return;
        // }

        boolean soundSwitch = false;
        String soundId = "";
        String type = "";
        String userId = "";

        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("greet-user-") || b.getCustomId().startsWith("greet-back-"))
                userId = b.getCustomId().split("-")[2];
        }

        if (!userId.equals(event.getUser().getId())) {
            event.deferReply(true).addContent("You can modify only your greets.").queue();
            return;
        }

        switch (args) {
            case "global":
                soundSwitch = true;
                soundId = UserCache.getUser(event.getUser().getId()).getGlobalGreet();
                type = "global";
                break;
            case "guild":
                soundSwitch = true;
                soundId = UserCache.getUser(event.getUser().getId()).getGreet(event.getGuild().getId());
                type = "guild";
                break;
            case "back":
                soundSwitch = false;
                break;
            case "set":
                System.out.println(clicked.getCustomId().split("-")[2]);
                type = clicked.getCustomId().split("-")[2];
                TextInput subject = TextInput.create("greet-set", "Select your " + type +" greet!", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id of the sound")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("greet-" + type, "Select your " + type +" greet!")
                        .addComponents(ActionRow.of(subject))
                        .build();

                event.replyModal(modal).queue();
                return;
            case "delete":
                type = clicked.getCustomId().split("-")[2];
                if (type.equals("global"))
                    UserCache.getUser(event.getUser().getId()).unsetGreet("0");
                else
                    UserCache.getUser(event.getUser().getId()).unsetGreet(event.getGuild().getId());
        }

        List<MessageTopLevelComponent> buttons = soundSwitch ? SoundEmbed.getGreetSoundButton(event.getUser().getId(), type, soundId) : SoundEmbed.getGreetButton(event.getUser().getId(), event.getGuild().getId());

        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(SoundEmbed.getGreetViewEmbed(event.getUser().getId(), event.getGuild().getId()).build())
                        .setComponents(buttons)
                        .queue();
    }

    private void playlist(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 2)[1];

        int page = 0;
        int playlistId = 0;
        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("playlist-center")) {
                playlistId = Integer.parseInt(b.getCustomId().split("-")[2]);
                page = Integer.parseInt(b.getLabel().split(" ")[1].trim()) - 1;
                break;
            }
        }

        QueryRecord playlist = BotDB.getPlaylistByIdWithSize(playlistId);
        switch (args) {
            case "left":
                page -= 1;
                break;
            case "right":
                page += 1;
                break;
        }

        event.getMessage().editMessageEmbeds(PlaylistView.getTracksEmbed(playlist, event.getMember(), page).build())
                .setComponents(PlaylistView.getTracksButton(playlist, page))
                .queue();


    }

    public void help(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 2)[1];


        BotCommand command = Help.searchCommand(args, CommandsLoader.getCommandsData(event.getUser().getId()));
        List<MessageTopLevelComponent> rows = Help.getCommandButton(command);


        if (rows != null) event.getMessage().editMessageEmbeds(Help.getCommandHelp(command).build()).setComponents(rows).queue();
        else event.getMessage().editMessageEmbeds(Help.getCommandHelp(command).build()).queue();
    }

    public void twitch(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];
        String streamerId = event.getButton().getCustomId().split("-", 3).length > 2 ? event.getButton().getCustomId().split("-", 3)[2] : "0";


        TextInput messageInput = null, privateInput = null, channelInput = null, roleInput = null;
        Modal modal = null;
        switch (args) {
            case "streamerId":
                event.deferEdit().queue();
                event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchStreamerEmbed(streamerId, event.getGuild().getId()).build())
                        .setComponents(TwitchMenu.getTwitchStreamerButtons(streamerId))
                        .queue();
                break;
            case "addSub":
                TextInput streamerInput = TextInput.create("twitch-streamer", "Streamer name", TextInputStyle.SHORT)
                    .setPlaceholder("sunny314_")
                    .setMinLength(4)
                    .setMaxLength(25)
                    .build();

                messageInput = TextInput.create("twitch-changeMessage", "New Message", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("#streamer is now live!")
                    .setMaxLength(1000)
                    .build();

                privateInput = TextInput.create("twitch-changePrivateMessage", "New Private Message", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live! (not required)")
                    .setRequired(false)
                    .setMaxLength(1000)
                    .build();

                channelInput = TextInput.create("twitch-changeChannel", "Channel Link/ID", TextInputStyle.SHORT)
                    .setPlaceholder("https://discord.com/channels/12345678912345678/123456789123456789")
                    .setMinLength(17)
                    .setMaxLength(100)
                    .build();

                roleInput = TextInput.create("twitch-changeRole", "Role to ping", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id (better) of the role")
                    .setRequired(false)
                    .setMaxLength(100)
                    .build();


                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(
                                ActionRow.of(streamerInput),
                                ActionRow.of(messageInput),
                                ActionRow.of(privateInput),
                                ActionRow.of(channelInput),
                                ActionRow.of(roleInput))
                        .build();

                event.replyModal(modal).queue();
                break;
            case "back":
                event.deferEdit().queue();
                event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchEmbed().build())
                        .setComponents(TwitchMenu.getTwitchButtons(event.getGuild().getId()))
                        .queue();
                break;
            case "changeMessage":
                messageInput = TextInput.create("twitch-changeMessage", "New Message", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live!")
                    .setMaxLength(1000)
                    .build();

                privateInput = TextInput.create("twitch-changePrivateMessage", "New Private Message", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live!")
                    .setRequired(false)
                    .setMaxLength(1000)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(ActionRow.of(messageInput), ActionRow.of(privateInput))
                        .build();

                event.replyModal(modal).queue();
                break;
            case "changeChannel":
                channelInput = TextInput.create("twitch-changeChannel", "Channel Link/ID", TextInputStyle.SHORT)
                    .setPlaceholder("https://discord.com/channels/12345678912345678/123456789123456789")
                    .setMinLength(17)
                    .setMaxLength(100)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(ActionRow.of(channelInput))
                        .build();

                event.replyModal(modal).queue();
                break;
            case "changeRole":
                roleInput = TextInput.create("twitch-changeRole", "Role to ping", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id (better) of the role")
                    .setMaxLength(100)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(ActionRow.of(roleInput))
                        .build();

                event.replyModal(modal).queue();
                break;
            case "delete":
                GuildCache.getGuildOrPut(event.getGuild().getId()).deleteAlert(AlertType.TWITCH, streamerId);

                if (BotDB.getTwitchSubscriptions(streamerId).size() == 0)
                    TwitchClient.unregisterSubEvent(streamerId);

                event.deferEdit().queue();
                event.getMessage().editMessageEmbeds(TwitchMenu.getTwitchEmbed().build())
                        .setComponents(TwitchMenu.getTwitchButtons(event.getGuild().getId()))
                        .queue();
                break;
            default:
                break;
        }
    }


    public void soundplay (ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];
        String soundId = event.getButton().getCustomId().split("-", 3)[2];

        PlayerManager pm = PlayerManager.get();
        Guild guild = event.getGuild();

        Sound sound = SoundCache.getSoundById(soundId);

        switch (args) {
            case "like":
                sound.like(event.getUser().getId(), !sound.hasLiked(event.getUser().getId()));
                break;
            case "dislike":
                sound.dislike(event.getUser().getId(), !sound.hasDisliked(event.getUser().getId()));
                break;
            case "replay":
                String path = sound.getPath();
                AudioChannel channelJoin = event.getMember().getVoiceState().getChannel();
                if (channelJoin == null)  return;

                sound.increaseUserPlays(event.getUser().getId());

                pm.loadItemOrdered(guild, path, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        if (!guild.getAudioManager().isConnected()) guild.getAudioManager().openAudioConnection(channelJoin);

                        track.setUserData(new TrackData(AudioType.SOUND));
                        pm.getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.SOUND);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {}

                    @Override
                    public void noMatches() {}

                    @Override
                    public void loadFailed(FriendlyException throwable) {
                        System.out.println("error: " + throwable.getMessage());
                    }
                });
                break;
            case "stop":
                pm.getGuildMusicManager(guild).getTrackScheduler().stop();
                break;

            default:
                break;
        }

        event.getMessage().editMessageEmbeds(SoundEmbed.getSoundEmbed(sound, event.getUser()).build())
                .setComponents(SoundEmbed.getSoundEmbedButtons(sound))
                .queue();
    }

    public void tag(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 4)[1];
        String soundId = event.getButton().getCustomId().split("-", 4)[2];
        String tagId = event.getButton().getCustomId().split("-", 4)[3];
        Sound soundData = SoundCache.getSoundById(soundId);

        boolean tagSwitch = true;
        switch (args) {
            case "back":
                tagSwitch = false;
                break;
            case "name":
                TextInput subject = TextInput.create("tag-name", "Tag Name", TextInputStyle.SHORT)
                .setPlaceholder("Change Tag")
                .setMaxLength(20)
                .build();

                Modal modal = Modal.create("tag-" + soundId + "-" + tagId, "Customize Your Sound")
                        .addComponents(ActionRow.of(subject))
                        .build();

                event.replyModal(modal).queue();
                return;
            case "delete":
                List<Tag> tags = soundData.getTags();
                for (int i = 0; i < tags.size(); i++) {
                    if (tags.get(i).getId() == Integer.parseInt(tagId)) {
                        tags.set(i, new Tag());
                        break;
                    }
                }
                soundData.setTags(tags);
                tagSwitch = false;
                break;
            default:
                break;
        }

        List<MessageTopLevelComponent> buttons = tagSwitch ? SoundEmbed.getTagButton(soundId, args) : SoundEmbed.getSoundButton(soundId);
        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(SoundCustomize.getEmbed(event.getUser(), soundData).build())
                        .setComponents(buttons)
                        .queue();

    }

    public void sound(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];
        String soundId = "";
        for (Button b : EventUtils.getButtons(event)) {
            if (b.getLabel().startsWith("ID"))
                soundId = b.getLabel().substring(b.getLabel().indexOf(":") + 2);
        }

        Button clicked = event.getButton();

        Sound soundData = SoundCache.getSoundById(soundId);
        int tagId = 0;

        if (!soundData.getUserId().equals(event.getUser().getId())) {
            event.deferReply(true).addContent("You can only modify your own sounds").queue();
            return;
        }

        boolean tagSwitch = false;

        switch (args) {
            case "name":
                TextInput subject = TextInput.create("sound-name", "Sound Name ( " + soundData.getName() + " )", TextInputStyle.SHORT)
                    .setPlaceholder("New Sound Name")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("sound-" + soundId, "Customize Your Sound")
                        .addComponents(ActionRow.of(subject))
                        .build();

                event.replyModal(modal).queue();
                return;
            case "private":
                boolean isPrivate = !soundData.isPublic();
                soundData.setPublic(isPrivate);
                break;
            case "delete":
                String response = SoundCache.deleteSound(soundId) ? "Sound deleted" : "Error deleting sound";
                event.deferReply(true).addContent(response).queue();
                return;
            case "tag":
                if (clicked.getStyle() == ButtonStyle.PRIMARY) tagId = Integer.parseInt(clicked.getCustomId().split("-")[3]);
                tagSwitch = true;
                break;
            case "download":
                File file = new File(soundData.getPath());
                event.getChannel().sendFiles(FileUpload.fromData(file)).queue();
                break;
        }

        List<MessageTopLevelComponent> buttons = tagSwitch ? SoundEmbed.getTagButton(soundId, String.valueOf(tagId)) : SoundEmbed.getSoundButton(soundId);

        event.deferEdit().queue();
        event.getMessage().editMessageEmbeds(SoundCustomize.getEmbed(event.getUser(), soundData).build())
                        .setComponents(buttons)
                        .queue();

    }


    public void reward(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-")[1];

        Guild guild = event.getGuild();

        Button left = Button.primary("reward-left", "<-");
        Button right = Button.primary("reward-right", "->");
        Button center = null;

        String level = "";
        for (Button b : EventUtils.getButtons(event)) {
            if (b.getLabel().startsWith("Level")) {
                level = b.getLabel().substring(b.getLabel().indexOf(":") + 2);
            }
        }

        switch (args) {
            case "right":
                RewardData nextReward = GuildCache.getGuildOrPut(guild.getId()).getHigherReward(Integer.parseInt(level));
                RewardData nextNextReward = GuildCache.getGuildOrPut(guild.getId()).getHigherReward(nextReward.getLevel());

                if (nextNextReward == null) {
                    right = right.asDisabled();
                    right = right.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("center", "Level: " + nextReward.getLevel());
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                event.getMessage().editMessageEmbeds(nextReward.getSampleEmbed(guild).build())
                        .setComponents(ActionRow.of(left, center, right))
                        .queue();
                break;

            case "left":

                RewardData previousRewardData = GuildCache.getGuildOrPut(guild.getId()).getLowerReward(Integer.parseInt(level));
                RewardData previousPreviousRewardData = GuildCache.getGuildOrPut(guild.getId()).getLowerReward(previousRewardData.getLevel());
                if (previousPreviousRewardData == null) {
                    left = left.asDisabled();
                    left = left.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("center", "Level: " + previousRewardData.getLevel());
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                event.getMessage().editMessageEmbeds(previousRewardData.getSampleEmbed(guild).build())
                        .setComponents(ActionRow.of(left, center, right))
                        .queue();


                break;
        }
    }

    public void queue(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-")[1];

        Guild guild = event.getGuild();

        PlayerManager pm = PlayerManager.get();
        TrackScheduler ts = pm.getGuildMusicManager(guild).getTrackScheduler();

        int previousIndex = ts.getIndex() - 11;
        if(previousIndex < 0)
            previousIndex = 0;

        int nextIndex = ts.getIndex() + 11;
        if(nextIndex > ts.getQueue().size())
            nextIndex = ts.getQueue().size() - 1;

        int startIndex = ts.getIndex();

        switch (args) {
            case "repeat":
                ts.setRepeat(!ts.isRepeat());
                break;
            case "previouspage":
                startIndex = Integer.parseInt(event.getButton().getCustomId().split("-", 3)[2]);
                if (startIndex < 0)
                    startIndex = 0;

                previousIndex = (startIndex == ts.getIndex() ? 0 : startIndex - 11);
                nextIndex = startIndex + 11;
                break;
            case "previous":
                ts.play(ts.getPrevious(), true);
                startIndex = ts.getIndex();
                break;
            case "pause":
                ts.pause(true);
                break;
            case "play":
                ts.pause(false);
                break;
            case "next":
                ts.play(ts.moveCursor(1), true);
                startIndex = ts.getIndex();
                break;
            case "nextpage":
                startIndex = Integer.parseInt(event.getButton().getCustomId().split("-")[2]);
                nextIndex = startIndex + 11;
                previousIndex = startIndex - 11;
                break;
            case "shurima":
                if (!ts.isShuffled())
                    ts.shuffleQueue();
                else
                    ts.unshuffleQueue();

                startIndex = ts.getIndex();
                previousIndex = startIndex - 11;
                if (previousIndex < 0)
                    previousIndex = 0;

                nextIndex = startIndex + 11;
                if (nextIndex > ts.getQueue().size())
                    nextIndex = ts.getQueue().size() - 1;
                break;
            case "clear":
                ts.clearQueue();
                break;
            case "player":
                ts.getMessage().setType(EmbedType.PLAYER);
                break;
            default:
                break;
        }

        List<MessageTopLevelComponent> rows = QueueHandler.getButtons(guild);
        if (ts.getMessage().getType() == EmbedType.QUEUE)
            rows = QueueHandler.getQueueButtons(guild, startIndex);

        EmbedBuilder eb = QueueHandler.getEmbed(guild);
        if (ts.getMessage().getType() == EmbedType.QUEUE)
            eb = QueueHandler.getQueueEmbed(guild, startIndex);

        event.getMessage().editMessageEmbeds(eb.build())
                .setComponents(rows).queue();
    }

    public void player(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-")[1];

        Guild guild = event.getGuild();

        PlayerManager pm = PlayerManager.get();
        TrackScheduler ts = pm.getGuildMusicManager(guild).getTrackScheduler();

        switch (args) {
            case "repeat":
                ts.setRepeat(!ts.isRepeat());
                break;
            case "rewind":
                ts.movePosition(-10);
                break;
            case "previous":
                ts.play(ts.getPrevious(), true);
                break;
            case "pause":
                ts.pause(true);
                break;
            case "play":
                ts.pause(false);
                break;
            case "next":
                ts.play(ts.moveCursor(1), true);
                break;
            case "forward":
                ts.movePosition(30);
                break;
            case "shurima":
                if (!ts.isShuffled())
                    ts.shuffleQueue();
                else
                    ts.unshuffleQueue();
                break;
            case "queue":
                ts.getMessage().setType(EmbedType.QUEUE);
                break;
            case "lyrics":
                event.getHook().sendMessageEmbeds(QueueHandler.getLyricsEmbed(guild).build()).queue();
                break;
            case "download":
                ts.downloadTrackAudio(ts.getCurrent(), event.getHook());
                break;
            default:
                break;
        }

        List<MessageTopLevelComponent> rows = QueueHandler.getButtons(guild);

        event.getMessage().editMessageEmbeds(QueueHandler.getEmbed(guild).build())
                .setComponents(rows).queue();
    }

    private void chat(ButtonInteractionEvent event) {
        String[] args = event.getButton().getCustomId().split("-");

        TextChannel channel = Bot.getJDA().getTextChannelById(args[2]);

        EmbedBuilder ebRequester = new EmbedBuilder();
        ebRequester.setAuthor(event.getGuild().getName(), event.getGuildChannel().getJumpUrl(), event.getGuild().getIconUrl());
        ebRequester.setTitle("Channel connection status");

        //EmbedBuilder ebReceiver = new EmbedBuilder();
        //ebRequester.setAuthor(channel.getGuild().getName(), channel.getJumpUrl(), channel.getGuild().getIconUrl());

        switch (args[1]) {
            case "refuse":
                ebRequester.setDescription("Channel connection refused");
                break;
            case "accept":
                ebRequester.setDescription("Channel connection accepted");
                ChatHandler.addConnection(event.getChannelId(), channel.getId());
                break;

            default:
                break;
        }
        channel.sendMessageEmbeds(ebRequester.build()).queue();
        event.getHook().editOriginalEmbeds((new EmbedBuilder()).setTitle("Connected").build()).setComponents(Collections.emptyList()).queue();
    }

    public void lolButtonEvent(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1).split("-")[0];

        String puuid = "";
        String region = "";

        int index = 0;

        String[] parts;
        String queueString = "";

        GameQueueType queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;

        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("lol-center-")) {
                puuid = b.getCustomId().split("-", 3)[2].substring(0, b.getCustomId().split("-", 3)[2].indexOf("#"));
                region = b.getCustomId().split("-", 3)[2].substring(b.getCustomId().split("-", 3)[2].indexOf("#") + 1);
            }
            else if (b.getCustomId().startsWith("lol-queue") && b.getStyle() == ButtonStyle.SUCCESS) {
                parts = b.getCustomId().split("-", 3);
                queueString = parts[2];
                queue = queueString.equals("all") ? null : GameQueueType.valueOf(queueString);
            }
        }

        String user_id = LeagueDB.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));

        if (user_id == null || user_id.isEmpty()) user_id = event.getUser().getId();
        HashMap<String, String> accounts = UserCache.getUser(user_id).getRiotAccounts();

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        int i = 0;
        for (String k : accounts.keySet()) {
            if (LeagueHandler.getSummonerByPuuid(k, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(k)))).getPUUID().equals(puuid)) {
                puuid = k;
                index = i;
                break;
            }
            i++;
        }

        String platform = "";

        long[] time = LeagueHandler.getCurrentSplitRange();

        switch (args) {

            case "right":
                if ((index + 1) == accounts.size()) index = 0;
                else index += 1;

                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));
                
                break;
            case "left":
                if (index == 0) index = accounts.size() - 1;
                else index -= 1;

                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));
                break;
            case "refresh":
                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("lol-center")) {
                        parts = b.getCustomId().split("-", 3);

                        puuid = parts[2].substring(0, parts[2].indexOf("#"));
                        platform = parts[2].substring(parts[2].indexOf("#") + 1);
                        break;
                    }
                }

                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";

                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(platform));
                LeagueHandler.clearSummonerCache(s);
                break;
            case "match":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";
                event.getMessage().editMessageEmbeds(LeagueMessage.getOpggEmbed(s).build()).setComponents(LeagueMessage.getOpggButtons(s, user_id, null, 0)).queue();
                return;
            case "rank":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";

                List<SpectatorParticipant> users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;

                StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users);
                EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users);
                List<MessageTopLevelComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, user_id));

                if (menu != null) {
                    row.add(0, ActionRow.of(menu));
                    event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();
                }
                else event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();

                return;
            case "shard":
                parts = event.getButton().getCustomId().split("-", 3);

                puuid = parts[2].substring(0, parts[2].indexOf("#"));
                platform = parts[2].substring(parts[2].indexOf("#") + 1);

                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(platform));
            break;
            case "queue":
                parts = event.getButton().getCustomId().split("-", 3);
                queueString = parts[2];
                queue = queueString.equals("all") ? null : GameQueueType.valueOf(queueString);

                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("lol-season") && b.getStyle() == ButtonStyle.SUCCESS) {
                        parts = b.getCustomId().split("-", 3);

                        switch (parts[2]) {
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
                        break;
                    }
                }
                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
            break;
            case "season":
                parts = event.getButton().getCustomId().split("-", 3);
                switch (parts[2]) {
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

                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("lol-queue") && b.getStyle() == ButtonStyle.SUCCESS) {
                        parts = b.getCustomId().split("-", 3);
                        queueString = parts[2];
                        queue = queueString.equals("all") ? null : GameQueueType.valueOf(queueString);
                        break;
                    }
                }
                if (EventUtils.getButtonById(event, "lol-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
            break;
            case "champion":
                if (EventUtils.getButtonById(event, "champion-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
                int summonerId = LeagueDB.getSummonerIdByPuuid(s.getPUUID(), s.getPlatform());
                LeagueMessage.sendChampionMessage(event.getHook(), user_id, LeagueMessageType.CHAMPION_GENERIC, s, summonerId, null, time[0], time[1], queue, null, false, 0); 
            return;
        }

        EmbedBuilder eb = LeagueMessage.getSummonerEmbed(s, time[0], time[1], queue);
        List<MessageTopLevelComponent> row = LeagueMessage.getSummonerButtons(s, user_id, time[0], time[1], queue);
        event.getMessage().editMessageEmbeds(eb.build()).setComponents(row).queue();

    }

    public void matchButtonEvent(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];

        String puuid = "";
        String region = "";
        int index = 0;

        GameQueueType queue = null;
        int page = 0;

        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("match-center-")) {
                puuid = b.getCustomId().split("-", 3)[2].substring(0, b.getCustomId().split("-", 3)[2].indexOf("#"));
                region = b.getCustomId().split("-", 3)[2].substring(b.getCustomId().split("-", 3)[2].indexOf("#") + 1);
            }
            if (b.getCustomId().startsWith("match-queue-") && b.getStyle() == ButtonStyle.SUCCESS) {
                queue = GameQueueType.valueOf(b.getCustomId().split("-")[2]);
            }
            if (b.getCustomId().startsWith("match-index-"))
                page = Integer.parseInt(b.getCustomId().split("-")[2]);
        }

        String user_id = LeagueDB.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));

        if (user_id == null || user_id.isEmpty()) user_id = event.getUser().getId();
        HashMap<String, String> accounts = UserCache.getUser(user_id).getRiotAccounts();

        int i = 0;
        for (String k : accounts.keySet()) {
            if (LeagueHandler.getSummonerByPuuid(k, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(k)))).getPUUID().equals(puuid)) {
                puuid = k;
                index = i;
                break;
            }
            i++;
        }

        String platform = "";
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        switch (args) {

            case "right":
                if ((index + 1) == accounts.size()) index = 0;
                else index += 1;

                puuid = (String) accounts.keySet().toArray()[index];
                page = 0;
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));

                break;

            case "left":
                if (index == 0) index = accounts.size() - 1;
                else index -= 1;

                puuid = (String) accounts.keySet().toArray()[index];
                page = 0;
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));

                break;
            case "refresh":
                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("match-center-")) {
                        String[] parts = b.getCustomId().split("-", 3);

                        puuid = parts[2].substring(0, parts[2].indexOf("#"));
                        platform = parts[2].substring(parts[2].indexOf("#") + 1);
                    }
                    else if (b.getCustomId().startsWith("match-queue-") && b.getStyle() == ButtonStyle.SUCCESS) {
                        queue = GameQueueType.valueOf(b.getCustomId().split("-")[2]);
                    }
                }

                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";

                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(platform));
                LOLMatch lastMatch = queue != null ? s.getLeagueGames().withCount(1).withQueue(queue).getMatchIterator().iterator().next() : s.getLeagueGames().withCount(1).getMatchIterator().iterator().next();
                MatchTracker.analyzeMatchHistory(lastMatch.getQueue(), s).queue();

                LeagueHandler.clearSummonerCache(s);
                break;
            case "queue":
                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("match-center-")) {
                        String[] parts = b.getCustomId().split("-", 3);

                        puuid = parts[2].substring(0, parts[2].indexOf("#"));
                        platform = parts[2].substring(parts[2].indexOf("#") + 1);
                        break;
                    }
                }

                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";

                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(platform));

                queue = event.getButton().getStyle() != ButtonStyle.SUCCESS ? GameQueueType.valueOf(event.getButton().getCustomId().split("-")[2]) : null;
                break;
            case "lol":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";

                event.getMessage().editMessageEmbeds(LeagueMessage.getSummonerEmbed(s).build()).setComponents(LeagueMessage.getSummonerButtons(s, user_id)).queue();
                return;
            case "rank":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";

                List<SpectatorParticipant> users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;

                StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users);
                EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users);
                List<MessageTopLevelComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, user_id));

                if (menu != null) {
                    row.add(0, ActionRow.of(menu));
                    event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();
                }
                else event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();

                return;
            case "match":
                page = 0;
                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
            break;
            case "matchleft":
                page = page - 5;
                if (page < 0) page = 0;
                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
            break;
            case "matchright":
                page = page + 5;

                if (EventUtils.getButtonById(event, "match-left") == null) user_id = "";
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
            break;
        }

        EmbedBuilder eb = LeagueMessage.getOpggEmbed(s, queue, page);

        event.getMessage().editMessageEmbeds(eb.build()).setComponents(LeagueMessage.getOpggButtons(s, user_id, queue, page)).queue();
    }

    public void rankButtonEvent(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);

        String puuid = "";
        String region = "";
        int index = 0;

        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("rank-center-")) {
                puuid = b.getCustomId().split("-", 3)[2].substring(0, b.getCustomId().split("-", 3)[2].indexOf("#"));
                region = b.getCustomId().split("-", 3)[2].substring(b.getCustomId().split("-", 3)[2].indexOf("#") + 1);
            }
        }

        String user_id = LeagueDB.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));

        if (user_id == null || user_id.isEmpty()) user_id = event.getUser().getId();
        HashMap<String, String> accounts = UserCache.getUser(user_id).getRiotAccounts();

        int i = 0;
        for (String k : accounts.keySet()) {
            if (LeagueHandler.getSummonerByPuuid(k, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(k)))).getPUUID().equals(puuid)) {
                puuid = k;
                index = i;
                break;
            }
            i++;
        }


        List<SpectatorParticipant> users = null;
        List<RiotAccount> riotAccounts = new ArrayList<>();


        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        RiotAccount account = null;
        switch (args) {

            case "right":
                if ((index + 1) == accounts.size())index = 0;
                else index += 1;

                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));

                account = LeagueHandler.getRiotAccountFromSummoner(s);
                riotAccounts.add(account);


                break;

            case "left":

                if (index == 0) index = accounts.size() - 1;
                else index -= 1;

                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));

                account = LeagueHandler.getRiotAccountFromSummoner(s);
                riotAccounts.add(account);

                break;
            case "refresh":
                String platform = "";
                for (Button b : EventUtils.getButtons(event)) {
                    if (!b.getCustomId().equals("rank-left") && !b.getCustomId().equals("rank-right") && !b.getCustomId().equals("rank-refresh")) {
                        String[] parts = b.getCustomId().split("-", 3);
                        puuid = parts[2].substring(0, parts[2].indexOf("#"));
                        platform = parts[2].substring(parts[2].indexOf("#") + 1);
                        break;
                    }
                }

                if (EventUtils.getButtonById(event, "rank-left") == null) user_id = "";

                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(platform));

                LeagueHandler.clearSummonerCache(s);

                account = LeagueHandler.getRiotAccountFromSummoner(s);
                riotAccounts.add(account);

                break;
            case "lol":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "rank-left") == null) user_id = "";

                event.getMessage().editMessageEmbeds(LeagueMessage.getSummonerEmbed(s).build()).setComponents(LeagueMessage.getSummonerButtons(s, user_id)).queue();
                return;
            case "match":
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

                if (EventUtils.getButtonById(event, "rank-left") == null) user_id = "";

                event.getMessage().editMessageEmbeds(LeagueMessage.getOpggEmbed(s).build()).setComponents(LeagueMessage.getOpggButtons(s, user_id, null, 0)).queue();

                return;
        }

        users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;
        StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users);
        EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users);
        List<MessageTopLevelComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, user_id));

        if (menu != null) {
            row.add(0, ActionRow.of(menu));
            event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();
        }
        else event.getMessage().editMessageEmbeds(builder.build()).setComponents(row).queue();

    }

    public void listButtonEvent(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);

        int page = 1;
        int cont = 0;

        Button left = Button.primary("list-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("list-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button order = Button.secondary("list-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));

        Button center = null;

        boolean timeOrder = false;
        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("list-order"))
                timeOrder = b.getStyle() == ButtonStyle.SUCCESS;
        }
        order = timeOrder ? order.withStyle(ButtonStyle.SUCCESS) : order.withStyle(ButtonStyle.SECONDARY);

        QueryResult sounds = BotDB.getlistGuildSounds(event.getGuild().getId(), timeOrder ? "time" : "name");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName(), "https://github.com/SafJNest",
                event.getUser().getAvatarUrl());
        eb.setTitle("List of " + event.getGuild().getName());
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setColor(Bot.getColor());
        eb.setDescription("Total Sound: " + sounds.size());

        switch (args) {

            case "right":
                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getLabel().startsWith("Page"))
                        page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                }

                cont = 24 * page;
                while (cont < (24 * (page + 1)) && cont < sounds.size()) {
                    String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if (24 * (page + 1) >= sounds.size()) {
                    right = right.asDisabled();
                    right = right.withStyle(ButtonStyle.DANGER);
                }
                center = Button.primary("center", "Page: " + (page + 1));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;

            case "left":

                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getLabel().startsWith("Page"))
                        page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                }
                cont = (24 * (page - 2) < 0) ? 0 : 24 * (page - 2);

                while (cont < (24 * (page - 1)) && cont < sounds.size()) {
                    String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if ((page - 1) == 1) {
                    left = left.asDisabled();
                    left = left.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("center", "Page: " + (page - 1));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;

            case "order":
                timeOrder = !timeOrder;

                order = timeOrder ? order.withStyle(ButtonStyle.SUCCESS) : order.withStyle(ButtonStyle.SECONDARY);
                sounds = BotDB.getlistGuildSounds(event.getGuild().getId(), timeOrder ? "time" : "name");

                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getLabel().startsWith("Page"))
                        page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                }

                cont = 24 * (page - 1);
                while (cont < (24 * page) && cont < sounds.size()) {
                    String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if (24 * (page + 1) >= sounds.size()) {
                    right = right.asDisabled();
                    right = right.withStyle(ButtonStyle.DANGER);
                }

                if (page == 1) {
                    left = left.asDisabled();
                    left = left.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("center", "Page: " + (page));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;
        }
        event.getMessage().editMessageEmbeds(eb.build())
                        .setComponents(ActionRow.of(left, center, right, order))
                        .queue();
    }

    public void listUserButtonEvent(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);

        int page = 1;
        int cont = 0;
        String userId = "";

        Button left = Button.primary("listuser-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("listuser-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button order = Button.secondary("listuser-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));
        Button center = null;

        boolean timeOrder = false;
        for (Button b : EventUtils.getButtons(event)) {
            if (b.getCustomId().startsWith("listuser-order"))
                timeOrder = b.getStyle() == ButtonStyle.SUCCESS;
        }
        order = timeOrder ? order.withStyle(ButtonStyle.SUCCESS) : order.withStyle(ButtonStyle.SECONDARY);

        for (Button b : EventUtils.getButtons(event)) {
            if (b.getLabel().startsWith("Page")) {
                page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                userId = b.getCustomId().split("-")[2];
            }
        }
        QueryResult sounds = null;
        if (!timeOrder) {
            sounds = (userId.equals(event.getMember().getId()))
                               ? BotDB.getlistUserSounds(userId)
                               : BotDB.getlistUserSounds(userId, event.getGuild().getId());
        } else {
            sounds = (userId.equals(event.getMember().getId()))
                               ? BotDB.getlistUserSoundsTime(userId)
                               : BotDB.getlistUserSoundsTime(userId, event.getGuild().getId());
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName(), "https://github.com/SafJNest",
                event.getUser().getAvatarUrl());
        eb.setTitle("List of " + event.getJDA().getUserById(userId).getName());
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setColor(Bot.getColor());
        eb.setDescription("Total Sound: " + sounds.size());


        switch (args) {

            case "right":
                cont = 24 * page;
                while (cont < (24 * (page + 1)) && cont < sounds.size()) {
                   String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if (24 * (page + 1) >= sounds.size()) {
                    right = right.asDisabled();
                    right = right.withStyle(ButtonStyle.DANGER);
                }
                center = Button.primary("listuser-center-" + userId, "Page: " + (page + 1));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;

            case "left":
                cont = (24 * (page - 2) < 0) ? 0 : 24 * (page - 2);

                while (cont < (24 * (page - 1)) && cont < sounds.size()) {
                    String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if ((page - 1) == 1) {
                    left = left.asDisabled();
                    left = left.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("listuser-center-" + userId, "Page: " + (page - 1));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;
            case "order":
                timeOrder = !timeOrder;

                order = timeOrder ? order.withStyle(ButtonStyle.SUCCESS) : order.withStyle(ButtonStyle.SECONDARY);
                if (!timeOrder) {
                    sounds = (userId.equals(event.getMember().getId()))
                                       ? BotDB.getlistUserSounds(userId)
                                       : BotDB.getlistUserSounds(userId, event.getGuild().getId());
                } else {
                    sounds = (userId.equals(event.getMember().getId()))
                                       ? BotDB.getlistUserSoundsTime(userId)
                                       : BotDB.getlistUserSoundsTime(userId, event.getGuild().getId());
                }

                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getLabel().startsWith("Page"))
                        page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                }

                cont = 24 * (page - 1);
                while (cont < (24 * page) && cont < sounds.size()) {
                    String locket = (!sounds.get(cont).getAsBoolean("public")) ? ":lock:" : "";
                    eb.addField("**"+sounds.get(cont).get("name")+"**" + locket, "ID: " + sounds.get(cont).get("id"), true);
                    cont++;
                }

                if (24 * (page + 1) >= sounds.size()) {
                    right = right.asDisabled();
                    right = right.withStyle(ButtonStyle.DANGER);
                }

                if (page == 1) {
                    left = left.asDisabled();
                    left = left.withStyle(ButtonStyle.DANGER);
                }

                center = Button.primary("listuser-center-" + userId, "Page: " + (page));
                center = center.withStyle(ButtonStyle.SUCCESS);
                center = center.asDisabled();
                break;
        }

        event.getMessage().editMessageEmbeds(eb.build())
            .setComponents(ActionRow.of(left, center, right, order))
            .queue();
    }


    private void banUserEvent(ButtonInteractionEvent event) {
        if(!event.getMember().hasPermission(Permission.BAN_MEMBERS)){
            event.deferReply().addContent("You don't have the permission to do that.").queue();
            return;
        }

        if(event.getButton().getStyle() != ButtonStyle.DANGER){
            event.editButton(event.getButton().withStyle(ButtonStyle.DANGER)).queue();
            return;
        }

        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);
        Member theGuy = event.getGuild().getMemberById(args);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName());
        eb.setTitle(theGuy.getUser().getName() + " has been banned");
        eb.setThumbnail(theGuy.getUser().getAvatarUrl());
        eb.setColor(Bot.getColor());
        Button pardon = Button.primary("unban-" + theGuy.getId(), "Pardon");
        event.getGuild().ban(theGuy, 0, TimeUnit.SECONDS).reason("Entered the blacklist").queue(
                    (e) -> event.getMessage().editMessageEmbeds(eb.build()).setComponents(ActionRow.of(pardon)).queue(),
                    new ErrorHandler().handle(
                        ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
                );

    }

    private void kickUserEvent(ButtonInteractionEvent event) {
        if(!event.getMember().hasPermission(Permission.KICK_MEMBERS)){
            event.deferReply().addContent("You don't have the permission to do that.").queue();
            return;
        }

        if(event.getButton().getStyle() != ButtonStyle.DANGER){
            event.editButton(event.getButton().withStyle(ButtonStyle.DANGER)).queue();
            return;
        }

        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);
        Member theGuy = event.getGuild().getMemberById(args);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName());
        eb.setTitle(theGuy.getUser().getName() + " has been kicked");
        eb.setThumbnail(theGuy.getUser().getAvatarUrl());
        eb.setColor(Bot.getColor());
        event.getGuild().kick(theGuy).reason("Entered the blacklist").queue(
            (e) -> event.getMessage().editMessageEmbeds(eb.build()).setComponents().queue(),
            new ErrorHandler().handle(
                ErrorResponse.MISSING_PERMISSIONS,
                (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
        );

    }

    private void ignoreUserEvent(ButtonInteractionEvent event) {
        if(!event.getMember().hasPermission(Permission.KICK_MEMBERS)){
            event.deferReply().addContent("You don't have the permission to do that.").queue();
            return;
        }
        event.getMessage().editMessageEmbeds(event.getMessage().getEmbeds().get(0)).setComponents().queue();

    }


    private void pardonUserEvent(ButtonInteractionEvent event) {

        String args = event.getButton().getCustomId().substring(event.getButton().getCustomId().indexOf("-") + 1);
        User theGuy = event.getJDA().getUserById(args);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getUser().getName());
        eb.setTitle(theGuy.getName() + " has been unbanned");
        eb.setThumbnail(theGuy.getAvatarUrl());
        eb.setColor(Bot.getColor());

        event.getGuild().unban(theGuy).queue(
            (e) -> event.getMessage().editMessageEmbeds(eb.build()).setComponents().queue(),
            new ErrorHandler().handle(
                ErrorResponse.MISSING_PERMISSIONS,
                (e) -> event.deferReply(true).addContent("Error. " + e.getMessage()).queue())
        );
    }


    private void champion(ButtonInteractionEvent event) {
        String args = event.getButton().getCustomId().split("-", 3)[1];

        String puuid = "";
        String region = "";
        int index = 0;

        GameQueueType queue = null;
        LeagueMessageType type = null;
        LaneType lane = null;
        StaticChampion champion = null;

        boolean showChampion = true;

        long[] time = LeagueHandler.getCurrentSplitRange();
        String timeString = "current";

        int offset = 0;

        for (Button b : EventUtils.getButtons(event)) {
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

            if (b.getCustomId().startsWith("champion-champion-")) {
                champion = LeagueHandler.getChampionById(Integer.parseInt(b.getCustomId().split("-")[2]));
                showChampion = b.getStyle() == ButtonStyle.SUCCESS;
            }
            
            if (b.getCustomId().startsWith("champion-season-") && b.getStyle() == ButtonStyle.SUCCESS)
                timeString = b.getCustomId().split("-")[2];

            if (b.getCustomId().startsWith("champion-leftpage")) {
                offset = Integer.parseInt(b.getCustomId().split("-")[2]);
            }

        }

        String user_id = LeagueDB.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));

        if (user_id == null || user_id.isEmpty()) user_id = event.getUser().getId();
        HashMap<String, String> accounts = UserCache.getUser(user_id).getRiotAccounts();

        int i = 0;
        for (String k : accounts.keySet()) {
            if (LeagueHandler.getSummonerByPuuid(k, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(k)))).getPUUID().equals(puuid)) {
                puuid = k;
                index = i;
                break;
            }
            i++;
        }

        offset = offset < 0 ? 0 : offset;
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        switch (args) {

            case "right":
                if ((index + 1) == accounts.size()) index = 0;
                else index += 1;

                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueHandler.getSummonerByPuuid(puuid, LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))));

                break;

            case "left":
                if (index == 0) index = accounts.size() - 1;
                else index -= 1;

                puuid = (String) accounts.keySet().toArray()[index];
                region = LeagueHandler.getShardFromOrdinal(Integer.parseInt(accounts.get(puuid))).getValue();
                break;
            case "queue":
                for (Button b : EventUtils.getButtons(event)) {
                    if (b.getCustomId().startsWith("champion-center-")) {
                        String[] parts = b.getCustomId().split("-", 3);

                        puuid = parts[2].substring(0, parts[2].indexOf("#"));
                        region = parts[2].substring(parts[2].indexOf("#") + 1);
                        break;
                    }
                }
                queue = event.getButton().getStyle() != ButtonStyle.SUCCESS ? GameQueueType.valueOf(event.getButton().getCustomId().split("-")[2]) : null;
            break;
            case "lane":
                lane = event.getButton().getStyle() != ButtonStyle.SUCCESS ? LaneType.valueOf(event.getButton().getCustomId().split("-")[2]) : null;
                break;
            case "type":
                type = LeagueMessageType.valueOf(event.getButton().getCustomId().split("-")[2]);
                switch (type) {
                    case CHAMPION_CHAMPIONS:
                        showChampion = false;
                    default:
                        break;
                }
                break;
            case "season":
                timeString = event.getButton().getCustomId().split("-", 3)[2];
                break;
            case "champion":
                showChampion = event.getButton().getStyle() != ButtonStyle.SUCCESS;
                break;
            case "change":
                TextInput subject = TextInput.create("champion-change", "Select a champion", TextInputStyle.SHORT)
                    .setPlaceholder("Champion name")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("champion-change", "Select a champion")
                        .addComponents(ActionRow.of(subject))
                        .build();

                event.replyModal(modal).queue();
                return;
            case "leftpage":
                offset = offset - 20;
                break;
            case "rightpage":
                System.err.println("efwefewf");
                offset = offset + 20;//dwedw
                System.out.println(offset);
                break;
        }

        event.deferEdit().queue();
        if (EventUtils.getButtonById(event, "champion-left") == null) user_id = "";
        s = LeagueHandler.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
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
        System.out.println("wwfvsf" + offset);
        offset = offset < 0 ? 0 : offset;
        showChampion = champion == null ? false : showChampion;
        System.out.println(offset);
        int summonerId = LeagueDB.getSummonerIdByPuuid(s.getPUUID(), s.getPlatform());
        LeagueMessage.sendChampionMessage(event.getHook(), user_id, type, s, summonerId, champion, time[0], time[1], queue, lane, showChampion, offset); 
    }
}
