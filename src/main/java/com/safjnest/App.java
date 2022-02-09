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
import com.safjnest.Commands.Audio.*;
import com.safjnest.Commands.ManageGuild.*;
import com.safjnest.Commands.ManageMembers.*;
import com.safjnest.Commands.Math.*;
import com.safjnest.Commands.Misc.*;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

/**
 * Main class.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a> Tier 1 Discord
 *         Admin
 * @author <a href="https://github.com/Leon412">Leon412</a> Tier 1 Manipulator
 */
public class App extends ListenerAdapter {
    private static JDA jda;
    private static String token;
    private static Activity activity = Activity.playing("Outplaying other bots | %help");
    private static String PREFIX = "%";
    private static final int maxBighi = 11700;
    private static final int maxPrime = (int) Integer.valueOf(maxBighi/5).floatValue();
    private static HashMap<String, String> tierOneLink = new HashMap<>();
    public static void main(String[] args) throws LoginException {
        //token = "OTM4NDg3NDcwMzM5ODAxMTY5.YfrAkQ.X9rOkLp1sLY1QNZXYY15jPF6BW0";
        token = "OTM5ODc2ODE4NDY1NDg4OTI2.Yf_Ofw.1Ql5INVXqLSPXYG7OxRaCD5A8bU";
        jda = JDABuilder
                .createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new App())
                .setActivity(activity)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .enableCache(CacheFlag.VOICE_STATE)
                .build();

        CommandClientBuilder builder = new CommandClientBuilder();
        builder.setPrefix(PREFIX);
        builder.setHelpWord("help");
        builder.setOwnerId("939876818465488926");
        
        builder.addCommand(new Ping());
        builder.addCommand(new Connect());
        builder.addCommand(new Disconnect());
        builder.addCommand(new Play(tierOneLink));
        builder.addCommand(new Bighi(maxBighi));
        builder.addCommand(new Prime(maxPrime));
        builder.addCommand(new Clear());
        builder.addCommand(new List());
        builder.addCommand(new Ban());
        builder.addCommand(new Kick());
        builder.addCommand(new Mute());
        builder.addCommand(new BugsNotifier());
        builder.addCommand(new Unban());
        builder.addCommand(new UnMute());
        builder.addCommand(new DAC());
        builder.addCommand(new FastestRoot());
        builder.addCommand(new Permissions());

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