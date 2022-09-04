/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */

package com.safjnest;

import java.util.HashMap;

import javax.security.auth.login.LoginException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;

import com.safjnest.Commands.Misc.*;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.PostgreSQL;
import com.safjnest.Utilities.TTSHandler;
import com.safjnest.Utilities.TheListener;
import com.safjnest.Commands.Math.*;
import com.safjnest.Commands.Advanced.SetWelcome;
import com.safjnest.Commands.Audio.*;
import com.safjnest.Commands.Dangerous.RandomMove;
import com.safjnest.Commands.Dangerous.VandalizeServer;
import com.safjnest.Commands.LOL.Champ;
import com.safjnest.Commands.LOL.FreeChamp;
import com.safjnest.Commands.LOL.RankMatch;
import com.safjnest.Commands.LOL.SetUser;
import com.safjnest.Commands.LOL.Summoner;
import com.safjnest.Commands.ManageGuild.*;
import com.safjnest.Commands.ManageMembers.*;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.impl.R4J;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

/**
 * Main class of the bot.
 * <p> The {@code JDA} is instantiated and his parameters are 
 * specified (token, activity, cache, ...). The bot connects to
 * discord and AWS S3. The bot's commands are instantiated.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @version 1.2.5
 */
public class App extends ListenerAdapter {
    private static boolean isCanary = true;
    private static JDA jda;
    private static String PREFIX;
    private static Activity activity;

    private static String token;
    private static String AWSAccesKey;
    private static String AWSSecretKey;
    private static String youtubeApiKey;
    private static String ttsApiKey;
    private static String riotKey;

    private static String hostName = "ec2-54-247-137-184.eu-west-1.compute.amazonaws.com";
    private static String database = "df52tpbes6100h";
    private static String user = "rtfcxffinekosr";
    private static String password = "b6f0333c60fb00cba040e063402257230c800242fc5fbdb68d490682d54434de";

    private static String bucket = "thebeebox";

    private static final int maxBighi = 11700;
    private static final int maxPrime = (int) Integer.valueOf(maxBighi/5).floatValue();

    private static HashMap<String, String> tierOneLink = new HashMap<>();

    /**
     * Metodo principale del bot.
     * @param args
     * @throws LoginException
     */
    public static void main(String[] args) throws LoginException {
        if(args.length > 0){
            isCanary = Boolean.parseBoolean(args[0]);
        }
        if(isCanary){
            System.out.println("[main] INFO Canary mode on");
            token = "OTM5ODc2ODE4NDY1NDg4OTI2.GXocIQ.IBIgxiPcrzQgQTaVtVi18AbVUElUHlwVKkp_1g";
            PREFIX = "$";
            activity = Activity.playing("ANNODAM OIDOZIR IEIDOCROPE BEAMBUZL BILLY");
            AWSAccesKey = "AKIASJG3D4LS4UT7VPX4";
            AWSSecretKey = "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";
            youtubeApiKey = "AIzaSyC1H92_8GzQmiL-GPZB2X8uqYgrP0rPOns";
            ttsApiKey = "d6199f5911f4493da571729f8127ce37";
            riotKey ="RGAPI-a87f4ffc-c676-4598-8e64-bed22e205eef";
        }
        else{
            System.out.println("[main] INFO Canary mode off");
            PREFIX = "p";
            activity = Activity.playing("Outplaying other bots | " + PREFIX + "help");
            token         = args[1];
            AWSAccesKey   = args[2];
            AWSSecretKey  = args[3];
            youtubeApiKey = args[4];
            ttsApiKey     = args[5];
            riotKey       = args[6];
        }

        TTSHandler tts = new TTSHandler(ttsApiKey);
        
        AwsS3 s3Client = new AwsS3(new BasicAWSCredentials(AWSAccesKey, AWSSecretKey), bucket);
        s3Client.initialize();

        R4J riotApi = null;
        try {
            riotApi = new R4J(new APICredentials(riotKey));
            System.out.println("[R4J] INFO Connection Successful!");
        } catch (Exception e) {
            System.out.println("[R4J] INFO Annodam Not Successful!");
        } 
        PostgreSQL sql = new PostgreSQL(hostName, database, user, password);
        TheListener listenerozzo = new TheListener(sql);
        jda = JDABuilder
            .createLight(token, GatewayIntent.MESSAGE_CONTENT ,GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
            .addEventListeners(listenerozzo)
            .setMemberCachePolicy(MemberCachePolicy.VOICE)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI)
            .build();

        CommandClientBuilder builder = new CommandClientBuilder();
        

        builder.setPrefix(PREFIX);
        builder.setHelpWord("helpme");
        builder.setOwnerId("939876818465488926");
        builder.setActivity(activity);
        
        //Audio
        builder.addCommand(new Connect());
        builder.addCommand(new DeleteSound(s3Client));
        builder.addCommand(new Disconnect());
        builder.addCommand(new DownloadSound(s3Client));
        builder.addCommand(new List(s3Client));
        builder.addCommand(new PlayYoutube(youtubeApiKey, tierOneLink));
        builder.addCommand(new PlaySound(s3Client));
        builder.addCommand(new Upload(s3Client));
        builder.addCommand(new TTS(tts));
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

        //Manage Member
        builder.addCommand(new Ban());
        builder.addCommand(new Unban());
        builder.addCommand(new Kick());
        builder.addCommand(new Move());
        builder.addCommand(new Mute());
        builder.addCommand(new UnMute());   
        builder.addCommand(new Image());
        builder.addCommand(new Permissions());
        builder.addCommand(new ModifyNickname());

        //Advanced
        builder.addCommand(new SetWelcome(sql));

        //Math
        builder.addCommand(new Bighi(maxBighi));
        builder.addCommand(new Prime(maxPrime));
        builder.addCommand(new DAC());
        builder.addCommand(new FastestRoot());
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

        CommandClient client = builder.build();
        jda.addEventListener(client);
        
        tierOneLink.put("QZayYolcq-g", "MERIO EPRIA DUE ZERO DUE ZERO CAMERETTA EEEEEEEEEEEEEPPPPPPPPPPPPPPPPPPPRRRRRRRRRRRRRRRRRRIIIIIIIIIIIIIIIIIIAAAAAAAAAAAA");
        tierOneLink.put("IaudNxuNtso", "MERIO EPRIA QUI COME UN COGLIONE A SFOGARSI I SENTIMINETI POKLVEWRE EEEEEEEEEEEPRIAAAAAAA LA LUCE DEL MEEEEEEERIO CON LE SUE RIME DA CAZZARO NE REPPER NE METALLARO NON È CHIARO LO STROZZINOIFHUWSHGFEIU0GHS0URGH");
        tierOneLink.put("Ed8I24y8QW4", "MERIO EPRIA HA SCOPERTO IL RAP DA POCO NON HA SENSO FARE PROGETTI SE NON SAI FARE NIENTE LA MAFIA VULCANO O VESUIO LAVA IL MERIO COL FUOCO MEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEERIO");
        tierOneLink.put("kP6Dg-a3p0k", "MERIO EPRIA TI PARLA DELLA SUA CITTA HA LA PISTOLA REHUGHYUW HGTUY9FWRYUH8GHY8U NON HAI CAPITO UN CAZZO DALLA VITA REUHGWUHGHWRU FECCIA DELL'UMANIT; RWUHGWUHGHUWGHUWRGHYUWRGWSUYIRELIWRGUHL NON SI CAPISCE UN CAZZO OIDOZIFER9 CANEKE");
        tierOneLink.put("D9G1VOjN_84", "IWAKE UP TO DE SOUND THE MUSIC THAT ALLWOA OOOOOOOOOOOH THE MISERTY, EVERYBODY WATNS OT BE MY ENBEMSYFWS=FGHEWUGTGWEG7 OHO RHTEUR08 7G9EGUH9TW9GUYH TW9");
        tierOneLink.put("zvNfGg5vKTs", "POVERO GABBIANOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO NON HAI VOGLIUA DI VOLARE SOPRA UNA SCOGLUIERAAAAAAAAAAAAAAAAAAAA HAI PERDUOT LA COMPAGNAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA NON TIO GF8REWHUG A VIDEF TI CAPISC JAAAAAA PEKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK");
    }
}
    