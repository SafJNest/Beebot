/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */
package com.safjnest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.awt.Color;
import java.text.MessageFormat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;

import com.safjnest.App;
import com.safjnest.commands.audio.Connect;
import com.safjnest.commands.audio.DeleteSound;
import com.safjnest.commands.audio.Disconnect;
import com.safjnest.commands.audio.Stop;
import com.safjnest.commands.audio.TTS;
import com.safjnest.commands.audio.greet.Greet;
import com.safjnest.commands.audio.list.List;
import com.safjnest.commands.audio.list.ListUser;
import com.safjnest.commands.audio.play.Play;
import com.safjnest.commands.audio.play.PlaySound;
import com.safjnest.commands.audio.play.PlayYoutube;
import com.safjnest.commands.audio.playlist.Playlist;
import com.safjnest.commands.audio.search.Search;
import com.safjnest.commands.audio.sound.Sound;
import com.safjnest.commands.audio.soundboard.Soundboard;
import com.safjnest.commands.guild.*;
import com.safjnest.commands.lol.*;
import com.safjnest.commands.lol.summoner.Summoner;
import com.safjnest.commands.math.*;
import com.safjnest.commands.members.*;
import com.safjnest.commands.members.blacklist.Blacklist;
import com.safjnest.commands.members.move.*;
import com.safjnest.commands.misc.*;
import com.safjnest.commands.misc.omegle.Omegle;
import com.safjnest.commands.misc.twitch.*;
import com.safjnest.commands.misc.spotify.*;
import com.safjnest.commands.owner.*;
import com.safjnest.commands.owner.Shutdown;
import com.safjnest.commands.queue.*;
import com.safjnest.commands.settings.*;
import com.safjnest.commands.settings.boost.Boost;
import com.safjnest.commands.settings.leave.Leave;
import com.safjnest.commands.settings.levelup.LevelUp;
import com.safjnest.commands.settings.reward.Reward;
import com.safjnest.commands.settings.welcome.Welcome;
import com.safjnest.core.audio.SoundEmbed;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.events.*;
import com.safjnest.model.BotSettings.BotSettings;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.util.AutomatedActionTimer;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;

/**
 * Main class of the bot.
 * <p>
 * The {@code JDA} is instantiated and his parameters are
 * specified (token, activity, cache, ...). The bot connects to
 * discord and AWS S3. The bot's commands are instantiated.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @version 4.0
 */
public class Bot {

    private static JDA jda;
    private static String botID;
    private static BotSettings settings;

    private static CommandClient client;

    /**
     * Where the magic happens <3.
     *
     */
    public void il_risveglio_della_bestia() {
        // fastest way to compile
        // ctrl c ctrl v
        // assembly:assembly -DdescriptorId=jar-with-dependencies

        //fastest way to comment
        //https://patorjk.com/software/taag/#p=display&c=c%2B%2B&f=Delta%20Corps%20Priest%201

        settings = SettingsLoader.getSettings().getBotSettings();

        BotLogger.warning(settings.getInfo());

        jda = JDABuilder
            .createLight(settings.getDiscordToken(), GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_EXPRESSIONS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MODERATION)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.ACTIVITY)
            .build();

        botID = jda.getSelfUser().getId();

        CommandClientBuilder builder = new CommandClientBuilder();
        builder.setHelpWord(settings.getHelpWord());
        builder.setOwnerId(settings.getOwnerId());
        builder.setCoOwnerIds(settings.getCoOwnersIds().toArray(new String[0]));

        Activity activity = Activity.listening(MessageFormat.format(settings.getActivity().replace("{0}", settings.getPrefix()), settings.getPrefix()));
        builder.setActivity(activity);
        //builder.setScheduleExecutor(null);
        //builder.forceGuildOnly("876606568412639272"); //server di leon
        //builder.forceGuildOnly("1150154886005133492"); //guitarrin
        //builder.forceGuildOnly("474935164451946506"); //safj
        
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                CustomEmojiHandler.loadEmoji();
                AutomatedActionTimer.init();
                BotLogger.info("Bot ready");
            }
        });

        builder.setPrefixFunction(event -> {
            if (event.getChannelType() == ChannelType.PRIVATE)
                return "";
            if (event.isFromGuild()) {
                GuildData gd = GuildCache.getGuildOrPut(event.getGuild());
                return gd == null ? settings.getPrefix() : gd.getPrefix();
            }
            return null;
        });

        if (App.isTesting()) {
            builder.setPrefixFunction(event -> {
                return settings.getPrefix();
            });
        }

        ArrayList<Command> commandsList = new ArrayList<Command>();
        Collections.addAll(commandsList, new PrintCache(), new Ping(), new Ram(), new Help(), new Prefix());

        Collections.addAll(commandsList, new Summoner(), new Augment(), new FreeChamp(), new Livegame(), 
            new Opgg(), new Calculator(), new Dice(), 
            new VandalizeServer(), new Jelly(), new Shutdown(), new Restart(), new Query(), new Alias(), new UltimateBravery());
  
        
        Collections.addAll(commandsList, new ChannelInfo(), new Clear(), new ServerInfo(), new MemberInfo(), new EmojiInfo(), 
            new InviteBot(), new Ban(), new Unban(), new Kick(), new Mute(), new UnMute(), new Image(), 
            new Permissions(), new ModifyNickname(), new RandomMove());

        
        Collections.addAll(commandsList, new Connect(), new Disconnect(), new List(), new ListUser(), 
            new PlayYoutube(), new PlaySound(), new TTS(), new Stop(), new Pause(), new Resume(), new Player(), new Queue(), 
            new Skip(), new Previous(), new PlayYoutubeForce(), new JumpTo(), new QRCode(), new Chat(), new Omegle(), new Soundboard(), new Warn()
        );
        
        Collections.addAll(commandsList, new Leaderboard(), new Test(), new ListGuild());
    
        builder.addCommands(commandsList.toArray(new Command[commandsList.size()]));

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new Ping(), new Bug(), new Help(), new Prefix());

        
        Collections.addAll(slashCommandsList, new Summoner(), new Augment(), new FreeChamp(), 
            new Livegame(),
            new Prime(settings.getMaxPrime()), new Calculator(), new Dice(), new Champion(), new Opgg(), 
            new Weather(), new APOD(), new SpecialChar(), new Spotify(),
            new Region(), new UltimateBravery(), new Item(), new QRCode()
        );
        
        
        Collections.addAll(slashCommandsList, new ChannelInfo(), new Clear(), new Msg(), 
            new ServerInfo(), new MemberInfo(), new EmojiInfo(), new InviteBot(), new Ban(), 
            new Unban(), new Kick(), new Move(),new Mute(), new UnMute(), new Image(), 
            new Permissions(), new ModifyNickname(), new Welcome(), new Leave(), new Boost(), 
            new Blacklist(), new Twitch(), new Omegle()
        );

        
        Collections.addAll(slashCommandsList, new DeleteSound(), new Disconnect(), 
            new List(), new Play(), new Playlist(), new TTS(), new Stop(), new Sound(),
            new Voice(), new Soundboard(), new Greet(), new Pause(), new Resume(),
            new Player(), new Queue(), new Skip(), new Previous(), new JumpTo(), new Search(), new AutomatedAction(), new Warn(), new Build()
        );

        Collections.addAll(slashCommandsList, new Reward(), new Leaderboard(), new LevelUp());

        builder.addSlashCommands(slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]));
        
        client = builder.build();
        
        client.setListener(new CommandEventHandler());

        jda.addEventListener(client);
        jda.addEventListener(new EventHandler());
        jda.addEventListener(new EventHandlerBeebot());
        jda.addEventListener(new EventButtonHandler());
        jda.addEventListener(new EventAutoCompleteInteractionHandler());
        jda.addEventListener(new EventModalInteractionHandler());
        jda.addEventListener(new EventComponentsHandler());
        SoundEmbed.getSoundboardSounds("10");
    }


    public void distruzione_demoniaca(){
        jda.shutdown();
    }

    public static JDA getJDA() {
        return jda;
    }

    public static BotSettings getSettings() {
        return settings;
    }

    public static String getPrefix() {
        return settings.getPrefix();
    }
    
    public static String getBotId() {
        return botID;
    }

    public static Color getColor() {
        return settings.getEmbedColor();
    }

    public static CommandClient getClient() {
        return client;
    }

    public static void handleEvent(GenericEvent event) {
        jda.getEventManager().handle(event);
    }
}
