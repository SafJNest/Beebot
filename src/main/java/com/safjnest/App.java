/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */

package com.safjnest;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.amazonaws.auth.BasicAWSCredentials;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;

import com.safjnest.Utilities.*;
import com.safjnest.Commands.LOL.*;
import com.safjnest.Commands.Misc.*;
import com.safjnest.Commands.Math.*;
import com.safjnest.Commands.Audio.*;
import com.safjnest.Commands.Dangerous.*;
import com.safjnest.Commands.ManageGuild.*;
import com.safjnest.Commands.ManageMembers.*;
import com.safjnest.Commands.ManageMembers.Move;
import com.safjnest.SlashCommands.Audio.*;
import com.safjnest.SlashCommands.LOL.*;
import com.safjnest.SlashCommands.ManageGuild.*;
import com.safjnest.SlashCommands.ManageMembers.*;
import com.safjnest.SlashCommands.Math.*;
import com.safjnest.SlashCommands.Misc.*;



import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.basic.APICredentials;
/**
 * Main class of the bot.
 * <p> The {@code JDA} is instantiated and his parameters are  
 * specified (token, activity, cache, ...). The bot connects to
 * discord and AWS S3. The bot's commands are instantiated.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @version 2.0
 */
public class App extends ListenerAdapter {
    private static JDA jda;
    public static String PREFIX;
    public static String botId;
    private static Activity activity;
    public static String color;
    private static String ownerID;
    private static String helpWord;

    private static String token;
    private static String AWSAccesKey;
    private static String AWSSecretKey;
    private static String youtubeApiKey;
    private static String ttsApiKey;
    private static String riotKey;

    private static String hostName;
    private static String database;
    private static String user;
    private static String password;

    private static String bucket;

    private static int maxPrime ;

    private static HashMap<String, String> tierOneLink = new HashMap<>();

    /**
     * Where the magic happens.
     * @param args
     */
    public static void main(String[] args) {
        SafJNest.bee();
        
        boolean isCanary=(args.length>0)?0>1:1>0;//extreme codegolfing

        JSONParser parser = new JSONParser();
        JSONObject settings = null, discordSettings = null, awsSettings = null, postgreSQLSettings = null;

        try (Reader reader = new FileReader("rsc" + File.separator + "settings.json")) {
            settings = (JSONObject) parser.parse(reader);
            settings = (JSONObject) settings.get((isCanary) ? "canary" : args[0]);
            discordSettings = (JSONObject) settings.get("DiscordSettings");
            awsSettings = (JSONObject) settings.get("AmazonAWS");
            postgreSQLSettings = (JSONObject) settings.get("PostgreSQL");
        } catch (Exception e) {
            e.printStackTrace();
        }

        PREFIX = discordSettings.get("prefix").toString();
        activity = Activity.playing(MessageFormat.format(discordSettings.get("activity").toString(), PREFIX));
        token = discordSettings.get("discordToken").toString();
        color = discordSettings.get("embedColor").toString();
        ownerID = discordSettings.get("ownerID").toString();
        helpWord = discordSettings.get("helpWord").toString();
        
        maxPrime = Integer.valueOf(discordSettings.get("maxPrime").toString());
        AWSAccesKey = awsSettings.get("AWSAccesKey").toString();
        AWSSecretKey = awsSettings.get("AWSSecretKey").toString();
        bucket = awsSettings.get("AWSbucketName").toString();
        
        youtubeApiKey = settings.get("youtubeApiKey").toString();
        ttsApiKey = settings.get("ttsApiKey").toString();
        riotKey = settings.get("riotKey").toString();

        hostName = postgreSQLSettings.get("HostName").toString();
        database = postgreSQLSettings.get("database").toString();
        user = postgreSQLSettings.get("user").toString();
        password = postgreSQLSettings.get("password").toString();

        System.out.println(discordSettings.get("info"));

        TTSHandler tts = new TTSHandler(ttsApiKey);
        
        PostgreSQL sql = new PostgreSQL(hostName, database, user, password);
        
        DatabaseHandler dbh = new DatabaseHandler(sql);
        dbh.doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed();
        
        AwsS3 s3Client = new AwsS3(new BasicAWSCredentials(AWSAccesKey, AWSSecretKey), bucket, sql);
        s3Client.initialize();

        R4J riotApi = null;
        try {
            riotApi = new R4J(new APICredentials(riotKey));
            System.out.println("[R4J] INFO Connection Successful!");
        } catch (Exception e) {
            System.out.println("[R4J] INFO Annodam Not Successful!");
        }  
        
        TheListener listenerozzo = new TheListener(sql);
        jda = JDABuilder
            .createLight(token, GatewayIntent.MESSAGE_CONTENT  ,GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
            .addEventListeners(listenerozzo)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER)
            .build();
        botId = jda.getSelfUser().getId();
        CommandClientBuilder builder = new CommandClientBuilder();
        //builder.setPrefix(PREFIX); 
        builder.setHelpWord(helpWord);
        builder.setOwnerId(ownerID);
        builder.setActivity(activity);

        builder.setPrefixFunction(event -> {
            if (event.getChannelType() == ChannelType.PRIVATE)
                return "";
            if (event.isFromGuild()) {
                GuildData gd = GuildSettings.getServer(event.getGuild().getId());
                return gd == null ? PREFIX : gd.getPrefix();
            }
            return null; 
        });
                
        //Audio
        builder.addCommand(new Connect());
        builder.addCommand(new DeleteSound(s3Client, sql));
        builder.addCommand(new Disconnect());
        builder.addCommand(new DownloadSound(s3Client, sql));
        builder.addCommand(new List(sql));
        builder.addCommand(new PlayYoutube(youtubeApiKey, tierOneLink));
        builder.addCommand(new PlaySound(s3Client, sql));
        builder.addCommand(new Upload(s3Client, sql));
        builder.addCommand(new TTS(tts, sql));
        builder.addCommand(new Stop());

        //Manage Guild
        builder.addCommand(new Anonym());
        builder.addCommand(new ChannelInfo());
        builder.addCommand(new Clear());
        builder.addCommand(new Msg());
        builder.addCommand(new ServerInfo());
        builder.addCommand(new UserInfo());
        builder.addCommand(new EmojiInfo());
        builder.addCommand(new InviteBot());
        builder.addCommand(new ListGuild());
        builder.addCommand(new SetPrefix(sql));
        builder.addCommand(new CustomEmbed());

        //Manage Member
        builder.addCommand(new Ban());
        builder.addCommand(new Unban());
        builder.addCommand(new Kick());
        builder.addCommand(new Move(sql));
        builder.addCommand(new Mute());
        builder.addCommand(new UnMute());      
        builder.addCommand(new Image());
        builder.addCommand(new Permissions());
        builder.addCommand(new ModifyNickname());
        builder.addCommand(new ListRoom(sql));

        //Advanced
        builder.addCommand(new SetWelcome(sql));
        builder.addCommand(new SetRoom(sql));
        builder.addCommand(new SetVoice(sql));

        //Math
        builder.addCommand(new Prime(maxPrime));
        builder.addCommand(new Calc());
        builder.addCommand(new Dice());

        //Dangerous
        builder.addCommand(new VandalizeServer());
        builder.addCommand(new RandomMove());

        //Misc
        builder.addCommand(new Ping());
        builder.addCommand(new BugsNotifier());
        builder.addCommand(new Ram());
        builder.addCommand(new Help());
        builder.addCommand(new Aliases());
        builder.addCommand(new RawMessage());
        builder.addCommand(new Jelly());
        builder.addCommand(new ThreadCounter());

        builder.addCommand(new Champ());
        builder.addCommand(new Summoner(riotApi, sql));
        builder.addCommand(new FreeChamp());
        builder.addCommand(new RankMatch(riotApi, sql));
        builder.addCommand(new SetUser(riotApi, sql));
        builder.addCommand(new LastMatches(riotApi, sql));
        
        
        // INSANE SLASH COMMAND DECLARATION
        
        
        //audio
        builder.addSlashCommand(new ConnectSlash());
        builder.addSlashCommand(new DeleteSoundSlash(s3Client, sql));
        builder.addSlashCommand(new DisconnectSlash());
        builder.addSlashCommand(new DownloadSoundSlash(s3Client, sql));
        builder.addSlashCommand(new ListSlash(sql));
        builder.addSlashCommand(new PlayYoutubeSlash(youtubeApiKey, tierOneLink));
        builder.addSlashCommand(new PlaySoundSlash(s3Client, sql));
        builder.addSlashCommand(new UploadSlash(s3Client, sql));
        builder.addSlashCommand(new TTSSlash(tts, sql));
        builder.addSlashCommand(new StopSlash());
        
        //Manage Guild
        builder.addSlashCommand(new ChannelInfoSlash());
        builder.addSlashCommand(new ClearSlash());
        builder.addSlashCommand(new ServerInfoSlash());
        builder.addSlashCommand(new UserInfoSlash());
        builder.addSlashCommand(new EmojiInfoSlash());
        builder.addSlashCommand(new SetWelcomeSlash(sql));
        builder.addSlashCommand(new SetVoiceSlash(sql));
        builder.addSlashCommand(new SetPrefixSlash(sql));

        //lol
        builder.addSlashCommand(new SummonerSlash(riotApi, sql));
        builder.addSlashCommand(new FreeChampSlash());
        builder.addSlashCommand(new RankMatchSlash(riotApi, sql));
        builder.addSlashCommand(new SetUserSlash(riotApi, sql));
        builder.addSlashCommand(new LastMatchesSlash(riotApi, sql));

        //Manage Member
        builder.addSlashCommand(new BanSlash());
        builder.addSlashCommand(new UnbanSlash());
        builder.addSlashCommand(new KickSlash());
        builder.addSlashCommand(new MoveSlash());
        builder.addSlashCommand(new MuteSlash());
        builder.addSlashCommand(new UnMuteSlash());      
        builder.addSlashCommand(new ImageSlash());
        builder.addSlashCommand(new PermissionsSlash());
        builder.addSlashCommand(new ModifyNicknameSlash());

        //Math
        builder.addSlashCommand(new PrimeSlash(maxPrime));
        builder.addSlashCommand(new DiceSlash());
        builder.addSlashCommand(new FunctionSlash());

        //Misc
        builder.addSlashCommand(new PingSlash());
        builder.addSlashCommand(new BugsNotifierSlash());
        builder.addSlashCommand(new RamSlash());
        builder.addSlashCommand(new HelpSlash());
        builder.addSlashCommand(new RawMessageSlash());
        builder.addSlashCommand(new ThreadCounterSlash());
        builder.addSlashCommand(new MsgSlash());
        builder.addSlashCommand(new InviteBotSlash());
        builder.addSlashCommand(new AnonymSlash());

        CommandClient client = builder.build();
        jda.addEventListener(client);
    }
}
