package com.safjnest.core.events;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.twitch.TwitchClient;
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
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.message.LeagueMessage;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.alert.AlertType;
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
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import com.safjnest.core.cache.managers.GuildCache;


public class EventButtonHandler extends ListenerAdapter {

    private record ButtonData(String value1, String value2, boolean active) {}

    private ButtonData getButtonData(ButtonInteractionEvent event, String prefix) {
        return getButtonData(event, prefix, 4);
    }

    private ButtonData getButtonData(ButtonInteractionEvent event, String prefix, int limit) {
        Button button = EventUtils.getButtonByPrefix(event, prefix);
        if (button == null) return new ButtonData("", "", false);
        return getButtonData(button, limit);
    }

    private ButtonData getButtonData(Button button) {
        return getButtonData(button, 4);
    }

    private ButtonData getButtonData(Button button, int limit) {
        String[] p = button.getCustomId().split("-", limit);
    
        String v1 = p.length >= 2 ? p[1] : "";
        String v2 = p.length >= 3 ? p[2] : "";
    
        return new ButtonData(v1, v2, button.getStyle() == ButtonStyle.SUCCESS);
    }

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

        else if (buttonId.startsWith(LeagueMessage.BUTTON_ID_PREFIX + "-")) {
            lol(event);
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

        if (buttonId.startsWith("list-"))
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
        Button clicked = event.getButton();
        String args = getButtonData(clicked).value1().trim();


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
                type = clicked.getCustomId().split("-")[2];
                TextInput subject = TextInput.create("greet-set", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id of the sound")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("greet-" + type, "Select your " + type +" greet!")
                        .addComponents(Label.of("Select your " + type + " greet!", subject))
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
        String args = getButtonData(event.getButton()).value1().trim();


        ButtonData buttonData = getButtonData(event, "playlist-center");
        int page = Integer.parseInt(buttonData.value2().trim()) - 1;
        int playlistId = Integer.parseInt(buttonData.value1().trim());

        QueryRecord playlist = BotDB.getPlaylistByIdWithSize(playlistId);
        switch (args) {
            case "left" -> page -= 1;
            case "right" -> page += 1;
        }

        event.getMessage().editMessageEmbeds(PlaylistView.getTracksEmbed(playlist, event.getMember(), page).build())
                .setComponents(PlaylistView.getTracksButton(playlist, page))
                .queue();
    }

    public void help(ButtonInteractionEvent event) {
        ButtonData buttonData = getButtonData(event.getButton());
        String args = buttonData.value1().trim();


        BotCommand command = Help.searchCommand(args, CommandsLoader.getCommandsData(event.getUser().getId()));
        List<MessageTopLevelComponent> rows = Help.getCommandButton(command);


        if (rows != null) event.getMessage().editMessageEmbeds(Help.getCommandHelp(command).build()).setComponents(rows).queue();
        else event.getMessage().editMessageEmbeds(Help.getCommandHelp(command).build()).queue();
    }

    public void twitch(ButtonInteractionEvent event) {
        String args = getButtonData(event.getButton()).value1().trim();
        String streamerId = getButtonData(event.getButton()).value2().trim();


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
                TextInput streamerInput = TextInput.create("twitch-streamer", TextInputStyle.SHORT)
                    .setPlaceholder("sunny314_")
                    .setMinLength(4)
                    .setMaxLength(25)
                    .build();

                messageInput = TextInput.create("twitch-changeMessage", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("#streamer is now live!")
                    .setMaxLength(1000)
                    .build();

                privateInput = TextInput.create("twitch-changePrivateMessage", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live! (not required)")
                    .setRequired(false)
                    .setMaxLength(1000)
                    .build();

                channelInput = TextInput.create("twitch-changeChannel", TextInputStyle.SHORT)
                    .setPlaceholder("https://discord.com/channels/12345678912345678/123456789123456789")
                    .setMinLength(17)
                    .setMaxLength(100)
                    .build();

                roleInput = TextInput.create("twitch-changeRole", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id (better) of the role")
                    .setRequired(false)
                    .setMaxLength(100)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(
                            Label.of("Streamer", streamerInput),
                            Label.of("Message", messageInput),
                            Label.of("Private Message", privateInput),
                            Label.of("Channel", channelInput),
                            Label.of("Role", roleInput)
                        )
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
                messageInput = TextInput.create("twitch-changeMessage", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live!")
                    .setMaxLength(1000)
                    .build();

                privateInput = TextInput.create("twitch-changePrivateMessage", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hello #streamer is now live!")
                    .setRequired(false)
                    .setMaxLength(1000)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(
                            Label.of("Message", messageInput),
                            Label.of("Private Message", privateInput)
                        )
                        .build();

                event.replyModal(modal).queue();
                break;
            case "changeChannel":
                channelInput = TextInput.create("twitch-changeChannel", TextInputStyle.SHORT)
                    .setPlaceholder("https://discord.com/channels/12345678912345678/123456789123456789")
                    .setMinLength(17)
                    .setMaxLength(100)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(Label.of("Channel", channelInput))
                        .build();

                event.replyModal(modal).queue();
                break;
            case "changeRole":
                roleInput = TextInput.create("twitch-changeRole", TextInputStyle.SHORT)
                    .setPlaceholder("Name or id (better) of the role")
                    .setMaxLength(100)
                    .build();

                modal = Modal.create("twitch-" + streamerId, "Modify Streamer Alert message")
                        .addComponents(Label.of("Role", roleInput))
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
        String args = getButtonData(event.getButton()).value1().trim();
        String soundId = getButtonData(event.getButton()).value2().trim();

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
        String args = getButtonData(event.getButton()).value1().trim();
        String soundId = getButtonData(event.getButton()).value2().trim();
        String tagId = event.getButton().getCustomId().split("-", 4)[3];
        Sound soundData = SoundCache.getSoundById(soundId);

        boolean tagSwitch = true;
        switch (args) {
            case "back":
                tagSwitch = false;
                break;
            case "name":
                TextInput subject = TextInput.create("tag-name", TextInputStyle.SHORT)
                .setPlaceholder("Change Tag")
                .setMaxLength(20)
                .build();

                Modal modal = Modal.create("tag-" + soundId + "-" + tagId, "Customize Your Sound")
                        .addComponents(Label.of("Tag Name", subject))
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
        Button clicked = event.getButton();
        
        String args = getButtonData(clicked).value1().trim();
        String soundId = getButtonData(event, "sound-id").value2().trim();


        Sound soundData = SoundCache.getSoundById(soundId);
        int tagId = 0;

        if (!soundData.getUserId().equals(event.getUser().getId())) {
            event.deferReply(true).addContent("You can only modify your own sounds").queue();
            return;
        }

        boolean tagSwitch = false;

        switch (args) {
            case "name":
                TextInput subject = TextInput.create("sound-name", TextInputStyle.SHORT)
                    .setPlaceholder("New Sound Name")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("sound-" + soundId, "Customize Your Sound")
                        .addComponents(Label.of("Sound Name ( " + soundData.getName() + " )", subject))
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

    public void queue(ButtonInteractionEvent event) {
        String args = getButtonData(event.getButton()).value1().trim();

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
                startIndex = Integer.parseInt(getButtonData(event.getButton()).value2().trim());
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
                startIndex = Integer.parseInt(getButtonData(event.getButton()).value2().trim());
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
        String args = getButtonData(event.getButton()).value1().trim();

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
        String args = getButtonData(event.getButton()).value1().trim();
        String content = getButtonData(event.getButton()).value2().trim();

        TextChannel channel = Bot.getJDA().getTextChannelById(content);

        EmbedBuilder ebRequester = new EmbedBuilder();
        ebRequester.setAuthor(event.getGuild().getName(), event.getGuildChannel().getJumpUrl(), event.getGuild().getIconUrl());
        ebRequester.setTitle("Channel connection status");

        //EmbedBuilder ebReceiver = new EmbedBuilder();
        //ebRequester.setAuthor(channel.getGuild().getName(), channel.getJumpUrl(), channel.getGuild().getIconUrl());

        switch (args) {
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

    public void listButtonEvent(ButtonInteractionEvent event) {
        String args = getButtonData(event.getButton()).value1().trim();

        int page = 1;
        int cont = 0;

        Button left = Button.primary("list-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("list-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button order = Button.secondary("list-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));

        Button center = null;

        ButtonData listOrderData = getButtonData(event, "list-order");
        boolean timeOrder = listOrderData.active();
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
        String args = getButtonData(event.getButton()).value1().trim();

        int page = 1;
        int cont = 0;
        String userId = "";

        Button left = Button.primary("listuser-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("listuser-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button order = Button.secondary("listuser-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));
        Button center = null;

        ButtonData listUserOrderData = getButtonData(event, "listuser-order");
        boolean timeOrder = listUserOrderData.active();
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

        String args = getButtonData(event.getButton()).value1().trim();
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

        String args = getButtonData(event.getButton()).value1().trim();
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


    private void lol(ButtonInteractionEvent event) {
        String args = getButtonData(event.getButton()).value1().trim();
        String content = getButtonData(event.getButton()).value2().trim();
        boolean active = getButtonData(event.getButton()).active();

        ButtonData lolCenterData = getButtonData(event, LeagueMessage.BUTTON_ID_PREFIX + "-center-", 3);
        if (lolCenterData.value2().isEmpty()) {
            lolCenterData = new ButtonData("#", "1#EUW1", false);
        }
        String puuid = lolCenterData.value2().trim().split("#")[0];
        String region = lolCenterData.value2().trim().split("#")[1];

        boolean userIdFallback = lolCenterData.active();

        LeagueMessageParameter parameter = new LeagueMessageParameter(EventUtils.getButtons(event));

        String user_id = LeagueService.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));
        if (user_id == null || user_id.isEmpty()) user_id = event.getUser().getId();
        HashMap<String, String> accounts = UserCache.getUser(user_id).getRiotAccounts();

        int index = 0;
        for (String k : accounts.keySet()) {
            if (k.equals(puuid)) {
                puuid = k;
                break;
            }
            index++;
        }

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        switch (args) {
            case "center":
            case "right":

                if ((index + 1) == accounts.size()) index = 0;
                else index += 1;
                puuid = (String) accounts.keySet().toArray()[index];
                s = LeagueService.getSummonerByPuuid(puuid, LeagueShard.valueOf(accounts.get(puuid)));

                break;

            case "left":
                if (index == 0) index = accounts.size() - 1;
                else index -= 1;

                puuid = (String) accounts.keySet().toArray()[index];
                region = accounts.get(puuid);
                break;
            case "queue":
                parameter.setQueueType(!active ? GameQueueType.valueOf(content) : null);
                parameter.setOffset(0);
            break;
            case "lane":
                parameter.setLaneType(!active ? LaneType.valueOf(content) : null);
                parameter.setOffset(0);
                break;
            case "type":
                parameter.setMessageType(LeagueMessageType.valueOf(content.toUpperCase()));
                switch (parameter.getMessageType()) {
                    case OVERVIEW_CHAMPIONS:
                        parameter.setShowChampion(false);
                    default:
                        break;
                }
                break;
            case "season":
                long[] time = new long[] {0, 0};
                switch (content) {
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
                parameter.setPeriod(time);
                parameter.setOffset(0);
                break;
            case "champion":
                 parameter.setShowChampion(!active);
                 parameter.setOffset(0);
                break;
            case "change":
                TextInput subject = TextInput.create("champion-change", TextInputStyle.SHORT)
                    .setPlaceholder("Champion name")
                    .setMaxLength(100)
                    .build();

                Modal modal = Modal.create("champion-change", "Select a champion")
                        .addComponents(Label.of("Select a champion", subject))
                        .build();

                event.replyModal(modal).queue();
                return;
            case "leftpage":
                parameter.setOffset(parameter.getOffset() - (parameter.getMessageType().getPageItem()));
                break;
            case "rightpage":
                parameter.setOffset(parameter.getOffset() + (parameter.getMessageType().getPageItem()));
                break;
            case "refresh":
                s = LeagueService.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));
                LeagueHandler.clearSummonerCache(s);
                try { Thread.sleep(500); } 
                catch (InterruptedException e) { }
                break;
        }

        event.deferEdit().queue();
        if (EventUtils.getButtonById(event, LeagueMessage.BUTTON_ID_PREFIX + "-left") == null && !userIdFallback) user_id = "";
        s = LeagueService.getSummonerByPuuid(puuid, LeagueShard.valueOf(region));

        int summonerId = s != null ? LeagueService.getSummonerIdByPuuid(s.getPUUID(), s.getPlatform()) : 0;
        LeagueMessage.send(event.getHook(), user_id, s, summonerId, parameter); 
    }
}