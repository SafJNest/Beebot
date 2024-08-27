/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */
package com.safjnest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.awt.Color;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
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
import com.safjnest.commands.audio.*;
import com.safjnest.commands.audio.slash.*;
import com.safjnest.commands.audio.slash.greet.GreetSlash;
import com.safjnest.commands.audio.slash.list.ListSlash;
import com.safjnest.commands.audio.slash.play.PlaySlash;
import com.safjnest.commands.audio.slash.search.SearchSlash;
import com.safjnest.commands.audio.slash.soundboard.SoundboardSlash;
import com.safjnest.commands.guild.*;
import com.safjnest.commands.guild.slash.*;
import com.safjnest.commands.lol.*;
import com.safjnest.commands.lol.slash.*;
import com.safjnest.commands.lol.slash.graph.GraphSlash;
import com.safjnest.commands.lol.slash.summoner.SummonerSlash;
import com.safjnest.commands.math.*;
import com.safjnest.commands.math.slash.*;
import com.safjnest.commands.members.*;
import com.safjnest.commands.members.slash.*;
import com.safjnest.commands.members.slash.blacklist.BlacklistSlash;
import com.safjnest.commands.members.slash.move.*;
import com.safjnest.commands.misc.*;
import com.safjnest.commands.misc.slash.*;
import com.safjnest.commands.misc.slash.twitch.*;
import com.safjnest.commands.owner.*;
import com.safjnest.commands.queue.*;
import com.safjnest.commands.queue.slash.*;
import com.safjnest.commands.settings.*;
import com.safjnest.commands.settings.slash.*;
import com.safjnest.commands.settings.slash.boost.BoostSlash;
import com.safjnest.commands.settings.slash.leave.LeaveSlash;
import com.safjnest.commands.settings.slash.levelup.LevelUpSlash;
import com.safjnest.commands.settings.slash.reward.RewardSlash;
import com.safjnest.commands.settings.slash.welcome.WelcomeSlash;
import com.safjnest.core.events.*;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;
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
public class Bot extends ListenerAdapter {

    private static JDA jda;
    private static String PREFIX;
    private static String BOT_ID;
    private static Color color;
    
    private Activity activity;
    private String ownerID;
    private String[] coOwnersIDs;
    private String helpWord;

    private String token;
    private String weatherApiKey;
    private String nasaApiKey;

    private int maxPrime;

    private static GuildDataHandler gs;
    private static CacheMap<String, UserData> userData;

    private static CommandClient client;

    /**
     * Where the magic happens.
     *
     */
    public void il_risveglio_della_bestia() {
        // fastest way to compile
        // ctrl c ctrl v
        // assembly:assembly -DdescriptorId=jar-with-dependencies

        //fastest way to comment
        //https://patorjk.com/software/taag/#p=display&c=c%2B%2B&f=Delta%20Corps%20Priest%201

        SettingsLoader settingsLoader = new SettingsLoader(App.getBot());

        PREFIX = settingsLoader.getPrefix();
        activity = settingsLoader.getActivity();
        token = settingsLoader.getDiscordToken();
        color = settingsLoader.getEmbedColor();
        ownerID = settingsLoader.getOwnerID();
        coOwnersIDs = settingsLoader.getCoOwnerIDs();
        helpWord = settingsLoader.getHelpWord();

        maxPrime = settingsLoader.getMaxPrime();
        weatherApiKey = settingsLoader.getWeatherAPIKey();
        nasaApiKey = settingsLoader.getNasaApiKey();

        BotLogger.warning(settingsLoader.getInfo());

        jda = JDABuilder
            .createLight(token, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_EMOJIS_AND_STICKERS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MODERATION)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.ACTIVITY)
            .build();

        BOT_ID = jda.getSelfUser().getId();

        gs = new GuildDataHandler();
        userData = new CacheMap<String, UserData>(50);
        
        CommandClientBuilder builder = new CommandClientBuilder();
        builder.setHelpWord(helpWord);
        builder.setOwnerId(ownerID);
        builder.setCoOwnerIds(coOwnersIDs);
        builder.setActivity(activity);
        //builder.forceGuildOnly("876606568412639272"); //server di leon
        //builder.forceGuildOnly("1150154886005133492"); //guitarrin
        //builder.forceGuildOnly("474935164451946506"); //safj
        
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                // jda.getGuilds().forEach(guild -> {
                //     guild.updateCommands().queue();
                // });
                // SubcommandData sub = new SubcommandData("menu", "Add a new twitch channel");
                // SlashCommandData scd = Commands.slash("twitch", "ffff").addSubcommands(
                //     sub
                // );
                // jda.getGuildById("474935164451946506").updateCommands().addCommands(
                //         scd
                // ).queue();
                new CustomEmojiHandler();
                BotLogger.debug("[JDA] Custom emoji cached correctly");
            }
        });

        builder.setPrefixFunction(event -> {
            if (event.getChannelType() == ChannelType.PRIVATE)
                return "";
            if (event.isFromGuild()) {
                GuildData gd = gs.getGuild(event.getGuild().getId());
                return gd == null ? PREFIX : gd.getPrefix();
            }
            return null;
        });

        if (App.isExtremeTesting()) {
            builder.setPrefixFunction(event -> {
                return PREFIX;
            });
        }

        new CommandsLoader();

        ArrayList<Command> commandsList = new ArrayList<Command>();
        Collections.addAll(commandsList, new PrintCache(gs), new Ping(), new Ram(), new Help(), new Prefix(gs));

        Collections.addAll(commandsList, new Summoner(), new Augment(), new FreeChamp(), new Livegame(), 
            new LastMatches(), new Opgg(), new Calculator(), new Dice(), 
            new VandalizeServer(), new Jelly(), new Shutdown(), new Restart(), new Query(), new Alias(), new UltimateBravery());
  
        
        Collections.addAll(commandsList, new ChannelInfo(), new Clear(), new ServerInfo(), new MemberInfo(), new EmojiInfo(), 
            new InviteBot(), new ListGuild(), new Ban(), new Unban(), new Kick(), new Mute(), new UnMute(), new Image(), 
            new Permissions(), new ModifyNickname(), new RandomMove());

        
        Collections.addAll(commandsList, new Connect(), new Disconnect(), new List(), new ListUser(), 
            new PlayYoutube(), new PlaySound(), new TTS(), new Stop(), new Pause(), new Resume(), new Player(), new Queue(), 
            new Skip(), new Previous(), new PlayYoutubeForce(), new JumpTo()
        );
        
        Collections.addAll(commandsList, new Leaderboard(), new Test(gs));
    
        builder.addCommands(commandsList.toArray(new Command[commandsList.size()]));

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new PingSlash(), new BugSlash(), new HelpSlash(), new PrefixSlash(gs));

        
        Collections.addAll(slashCommandsList, new SummonerSlash(), new AugmentSlash(), new FreeChampSlash(), 
            new LivegameSlash(), new LastMatchesSlash(), new GraphSlash(),
            new PrimeSlash(maxPrime), new CalculatorSlash(), new DiceSlash(), new ChampionSlash(), new OpggSlash(), 
            new WeatherSlash(weatherApiKey), new APODSlash(nasaApiKey), new SpecialCharSlash(), new RegionSlash(), new UltimateBraverySlash(), new ItemSlash()
        );
        
        
        Collections.addAll(slashCommandsList, new ChannelInfoSlash(), new ClearSlash(), new MsgSlash(), 
            new ServerInfoSlash(), new MemberInfoSlash(), new EmojiInfoSlash(), new InviteBotSlash(), new BanSlash(), 
            new UnbanSlash(), new KickSlash(), new MoveSlash(),new MuteSlash(), new UnMuteSlash(), new ImageSlash(), 
            new PermissionsSlash(), new ModifyNicknameSlash(), new WelcomeSlash(gs), new LeaveSlash(), new BoostSlash(), 
            new BlacklistSlash(gs), new TwitchSlash()
        );

        
        Collections.addAll(slashCommandsList, new DeleteSoundSlash(), new DisconnectSlash(), new DownloadSoundSlash(), 
            new ListSlash(), new PlaySlash(), new UploadSlash(), new TTSSlash(), new StopSlash(), 
            new VoiceSlash(), new CustomizeSoundSlash(), new SoundboardSlash(), new GreetSlash(), new PauseSlash(), new ResumeSlash(),
            new PlayerSlash(), new QueueSlash(), new SkipSlash(), new PreviousSlash(), new JumpToSlash(), new SearchSlash()
        );

        Collections.addAll(slashCommandsList, new RewardSlash(), new LeaderboardSlash(), new LevelUpSlash(gs));

        builder.addSlashCommands(slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]));
        
        client = builder.build();
        
        client.setListener(new CommandEventHandler());

        jda.addEventListener(client);
        jda.addEventListener(new EventHandler());
        jda.addEventListener(new EventButtonHandler());
        jda.addEventListener(new EventAutoCompleteInteractionHandler());
        jda.addEventListener(new EventHandlerBeebot());
        jda.addEventListener(new EventModalInteractionHandler());

        if(App.isExtremeTesting()){
            //Connection c = new Connection(jda, gs, bs);
            //c.start();
        }
        
    }


    public void distruzione_demoniaca(){
        jda.shutdown();
    }

    public static JDA getJDA() {
        return jda;
    }

    public static String getPrefix() {
        return PREFIX;
    }

    public static String getBotId() {
        return BOT_ID;
    }

    public static Color getColor() {
        return color;
    }

    public static GuildDataHandler getGuildSettings() {
        return gs;
    }

    public static GuildData getGuildData(Guild guild) {
        return getGuildData(guild.getId());
    }

    public static GuildData getGuildData(String id) {
        return gs.getGuild(id);
    }

    public static CommandClient getClient() {
        return client;
    }

    public static UserData getUserData(String userId) {
        if (!userData.containsKey(userId)) {
            userData.put(userId, new UserData(userId));
        }
        return userData.get(userId);
    }

    public static CacheMap<String, UserData> getUsers() {
        return userData;
    }
}
