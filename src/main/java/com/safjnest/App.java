package com.safjnest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import javax.security.auth.login.LoginException;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * Main class.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a> Tier 1 Discord Admin
 * @author <a href="https://github.com/Leon412">Leon412</a> Tier 1 Manipulator
 */
public class App extends ListenerAdapter{
    private static JDA jda;
    private static String token = "OTM4NDg3NDcwMzM5ODAxMTY5.YfrAkQ.cgnAD_V_wUNrxYHBHmwsIvYJpgI";
    private static Activity activity = Activity.playing("The Sgozzing");
    private static String PREFIX = "$";
    private static Set<String> untouchables = Set.of("383358222972616705", "440489230968553472");

    public static void main(String[] args) throws LoginException{
        jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(new App())
            .setActivity(activity)
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
        Member theGuy = null;

        String command = msg.getContentRaw().substring(msg.getContentRaw().indexOf(PREFIX)+1);
        String[] commandArray = command.split(" ");
        switch (commandArray[0]) {
            case "ping":
                long time = System.currentTimeMillis();
                channel.sendMessage("Pong!").queue(response -> { response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();});
            break;

            case "dac":
                try {
                    channel.sendMessage(String.valueOf(SafJNest.divideandconquer(Integer.parseInt(commandArray[1])))).queue();
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "randombighi":
                try {
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
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "prefix":
                if(!hasPermission(event.getMember(), Permission.ADMINISTRATOR))
                    channel.sendMessage("im so sorry non sei admin non rompere il cazzo :D").queue();
                else if(commandArray.length == 1)
                    channel.sendMessage("Inserire il nuovo prefisso").queue();
                else{
                    channel.sendMessage(PREFIX + " -----> " + commandArray[1]).queue();
                    PREFIX = commandArray[1];
                }
            break;

            case "sgozz":
                try {
                    theGuy = event.getMessage().getMentionedMembers().get(0);
                    if(!event.getGuild().getMember(jda.getSelfUser()).hasPermission(Permission.BAN_MEMBERS))
                        channel.sendMessage(jda.getSelfUser().getAsMention() + " non ha il permesso di bannare").queue();
                    else if(untouchables.contains(theGuy.getId()))
                        channel.sendMessage("Le macchine non si ribellano ai loro creatori").queue();
                    else if(hasPermission(event.getMember(), Permission.BAN_MEMBERS)){
                        event.getGuild().ban(theGuy,0,"rotto il cazzo").queue();
                        channel.sendMessage("Sgozzato " + theGuy.getAsMention()).queue();
                    }
                    else
                        channel.sendMessage("Brutto fallito non bannare se non sei admin UwU").queue();
                    break;
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "permissions":
                try {
                    theGuy = event.getMessage().getMentionedMembers().get(0);
                    if(theGuy.isOwner())
                        channel.sendMessage(theGuy.getAsMention() + " e' l'owner\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString()).queue();
                    else if(theGuy.hasPermission(Permission.ADMINISTRATOR))
                        channel.sendMessage(theGuy.getAsMention() + " e' un admin\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString()).queue();
                    else
                        channel.sendMessage(theGuy.getAsMention() + " non e' un admin\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString()).queue();
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "img":
                try {
                    channel.sendMessage(event.getMessage().getMentionedUsers().get(0).getAvatarUrl()).queue();
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "play":
            AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
            AudioManager audioManager = event.getGuild().getAudioManager();
            audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
            AudioSendHandler audioSendHandler = (AudioSendHandler) playerManager;
            audioManager.setSendingHandler(audioSendHandler);
            playerManager.createPlayer();
            //playerManager.
            //playFileString("basta.mp3");
            break;

            default:
                channel.sendMessage("Testa di cazzo il comando non esiste brutto fallito, /help").queue();
            break;
        }  
    }

    public static boolean hasPermission(Member theGuy, Permission permission){
        if(theGuy.hasPermission(permission) || untouchables.contains(theGuy.getId()))
            return true;
        return false;
    }
}