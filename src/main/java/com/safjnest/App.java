/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */

package com.safjnest;

import java.util.HashMap;

import javax.security.auth.login.LoginException;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;

import com.safjnest.Commands.Misc.*;
import com.safjnest.Utilities.TheListener;
import com.safjnest.Commands.Math.*;
import com.safjnest.Commands.Audio.*;
import com.safjnest.Commands.ManageGuild.*;
import com.safjnest.Commands.ManageMembers.*;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

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
    private static String token;
    private static String PREFIX;
    private static Activity activity;

    private static String AWSAccesKey;
    private static String AWSSecretKey;

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
            activity = Activity.playing("taking jelly's head and slauthering it because he is a fucking bot oidozir annodam ieidocrope jelly fix the faker dashboard");
            AWSAccesKey = "AKIASJG3D4LS4UT7VPX4";
            AWSSecretKey = "9RlRQCIJlCCYTLdg/Y9DiDHUQXjt6/6fhzohM/su";
        }
        else{
            System.out.println("[main] INFO Canary mode off");
            token = args[1];
            PREFIX = "%";
            activity = Activity.playing("Outplaying other bots | " + PREFIX + "help");
            AWSAccesKey = args[2];
            AWSSecretKey = args[3];
        }

        AWSCredentials credentials = new BasicAWSCredentials(AWSAccesKey, AWSSecretKey);
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");

        AmazonS3 s3Client = AmazonS3ClientBuilder
            .standard()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("s3.us-east-1.amazonaws.com", "us-east-1"))
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(clientConfiguration)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();

        try {
            s3Client.doesBucketExistV2("thebeebox");
        } catch (SdkClientException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("[AWS] INFO Connection Successful!");
        
        jda = JDABuilder
            .createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS)
            .addEventListeners(new TheListener())
            .setMemberCachePolicy(MemberCachePolicy.VOICE)
            .setChunkingFilter(ChunkingFilter.ALL)
            .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOTE)
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
        builder.addCommand(new Play(tierOneLink));
        builder.addCommand(new PlaySound(s3Client));
        builder.addCommand(new Upload(s3Client));

        //Manage Guild
        builder.addCommand(new Anonym());
        builder.addCommand(new ChannelInfo());
        builder.addCommand(new Clear());
        builder.addCommand(new Msg());
        builder.addCommand(new ServerInfo());
        builder.addCommand(new UserInfo());
        builder.addCommand(new EmojiInfo());

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

        //Math
        builder.addCommand(new Bighi(maxBighi));
        builder.addCommand(new Prime(maxPrime));
        builder.addCommand(new DAC());
        builder.addCommand(new FastestRoot());
        builder.addCommand(new Calc());
        builder.addCommand(new Dice());
        //Misc
        builder.addCommand(new Ping());
        builder.addCommand(new BugsNotifier());
        builder.addCommand(new Ram());
        builder.addCommand(new Help());
        builder.addCommand(new Aliases());
        builder.addCommand(new RawMessage());
        builder.addCommand(new Jelly());
        builder.addCommand(new Info());

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
    