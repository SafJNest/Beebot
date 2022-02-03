package com.safjnest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
//import net.dv8tion.jda.core.events.message.guild;
/**
 * Main class.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a> Tier 1 Discord Admin
 * @author < href="https://github.com/Leon412">Leon412</   a> Tier 1 Manipulator
 */
public class App extends ListenerAdapter{
    private static JDA jda;
    private static String PREFIX = "$";
    private static String idAdminRole = "694980596291862548";
    public static void main( String[] args ) throws LoginException{
        jda = JDABuilder.createLight("OTM4NDg3NDcwMzM5ODAxMTY5.YfrAkQ.cgnAD_V_wUNrxYHBHmwsIvYJpgI", GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(new App())
            .setActivity(Activity.playing("Sgozzing"))
            .setMemberCachePolicy(MemberCachePolicy.VOICE)
            .enableCache(CacheFlag.VOICE_STATE) 
            .build();
        
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        Message msg = event.getMessage();
        if(!msg.getContentRaw().startsWith(PREFIX)) 
            return;
        MessageChannel channel = event.getChannel(); 
        
        if(!msg.getChannel().getId().equals("938513359626715176") && !msg.getChannel().getId().equals("733274067917996123")){
            channel.sendMessage("DIO CANE MANDA I MESSAGGI NEL CANALE GIUSTO").queue();
            return;
        }
        String command = msg.getContentRaw().substring(msg.getContentRaw().indexOf(PREFIX)+1);
        String[] commandArray = command.split(" "); 
        switch (commandArray[0]) {
            case "ping":
                long time = System.currentTimeMillis();
                channel.sendMessage("Pong!").queue(response -> { response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();});
            break;

            case "numero":
                channel.sendMessage("QUARANTATRESEDICIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII").queue();
            break;

            case "marchio":
                channel.sendMessage("LIUUUUUUUUUK QUARANTATRESEDICIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII").queue();
            break;

            case "passione":
                channel.sendMessage("LA MIA PASSIONE E' MINCRAAAAAAAAAAAAAAAAAAAAAAAAAAAAFT").queue();
            break;

            case "divideandconquer":
                if(commandArray.length == 2 && SafJNest.intIsParsable(commandArray[1])) {
                    channel.sendMessage(String.valueOf(SafJNest.divideandconquer(Integer.parseInt(commandArray[1])))).queue();
                    break;
                }
                channel.sendMessage("metti l'odiozir di numerino pls").queue();
            break;

            case "randombighi":
                if(commandArray.length == 1){
                    channel.sendMessage("metti l'odiozir di numerino pls").queue();
                    break;
                }
                String bighi = String.valueOf(SafJNest.randomBighi(Integer.parseInt(commandArray[1])));
                if(bighi.length() > 2000){
                    File supp = new File("bighi.txt");
                    FileWriter app;
                    try {
                        app = new FileWriter(supp);
                        app.write(bighi);
                        app.flush();
                        app.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                    channel.sendFile(supp).queue();   
                    break;
                }
                channel.sendMessage("Eccoti il tuo bighi a " + commandArray[1] + " bit").queue();
                channel.sendMessage(bighi).queue();
            break;

            case "prefix":
                if(!hasRole(idAdminRole, event)){
                    channel.sendMessage("im so sorry non sei admin non rompere il cazzo :D").queue();
                    break;
                }
                if(commandArray.length == 1){
                    channel.sendMessage("Inserire il nuovo prefisso").queue();
                    break;
                }
                channel.sendMessage(PREFIX + " -----> " + commandArray[1]).queue();
                PREFIX = commandArray[1];
            break;

            case "sgozz":
                if(!hasRole(idAdminRole, event)){
                    channel.sendMessage("Brutto fallito non bannare se non sei admin UwU").queue();
                    break;
                }
                Guild guild = event.getGuild();
                Member member = event.getMessage().getMentionedMembers().get(0);
                channel.sendMessage("Sgozzato @" + member.getNickname());
                guild.ban(member,0,"rotto il cazzo").queue();

            break;
                case "play":
                //AudioManager audioManager = event.getGuild().getAudioManager();
                //audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
                //AudioSendHandler audioSendHandler = ;
                //audioManager.setSendingHandler(audioSendHandler);
                //playFileString(audioFile.getAbsolutePath());
                break;

            default:
                channel.sendMessage("Testa di cazzo il comando non esiste brutto fallito, /help").queue();
            break;
        }  
    }

    public static boolean hasRole(String idRole, MessageReceivedEvent event){
        for(Role role : event.getMember().getRoles()){
            if(role.getId().equals(idRole))
                return true;
        }
        return false;
    }

}