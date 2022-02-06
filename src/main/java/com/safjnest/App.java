package com.safjnest;
/**
 * Copyright (c) 22 Giugno anno 0, 2022, SafJNest and/or its affiliates. All rights reserved.
 * SAFJNEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * 
 */
import java.io.File;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;

import java.time.Duration;

import javax.security.auth.login.LoginException;

import java.awt.Color;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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
    private static Activity activity = Activity.playing("Outplaying other bots | $help");
    private static String PREFIX = "$";
    private static Set<String> untouchables = Set.of("383358222972616705", "440489230968553472");
    private static AudioPlayer player;
    private static final int maxBighi = 11700;
    private static final int maxPrime = (int) Integer.valueOf(maxBighi/5).floatValue();
    private static HashMap<String, String> tierOneLink = new HashMap<>();
    public static void main(String[] args) throws LoginException {
        token = "OTM4NDg3NDcwMzM5ODAxMTY5.YfrAkQ.X9rOkLp1sLY1QNZXYY15jPF6BW0";
        jda = JDABuilder
                .createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new App())
                .setActivity(activity)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .enableCache(CacheFlag.VOICE_STATE)
                .build();
                
        tierOneLink.put("QZayYolcq-g", "MERIO EPRIA DUE ZERO DUE ZERO CAMERETTA EEEEEEEEEEEEEPPPPPPPPPPPPPPPPPPPRRRRRRRRRRRRRRRRRRIIIIIIIIIIIIIIIIIIAAAAAAAAAAAA");
        tierOneLink.put("IaudNxuNtso", "MERIO EPRIA QUI COME UN COGLIONE A SFOGARSI I SENTIMINETI POKLVEWRE EEEEEEEEEEEPRIAAAAAAA LA LUCE DEL MEEEEEEERIO CON LE SUE RIME DA CAZZARO NE REPPER NE METALLARO NON È CHIARO LO STROZZINOIFHUWSHGFEIU0GHS0URGH");
        tierOneLink.put("Ed8I24y8QW4", "MERIO EPRIA HA SCOPERTO IL RAP DA POCO NON HA SENSO FARE PROGETTI SE NON SAI FARE NIENTE LA MAFIA VULCANO O VESUIO LAVA IL MERIO COL FUOCO MEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEERIO");
        tierOneLink.put("kP6Dg-a3p0k", "MERIO EPRIA TI PARLA DELLA SUA CITTA HA LA PISTOLA REHUGHYUW HGTUY9FWRYUH8GHY8U NON HAI CAPITO UN CAZZO DALLA VITA REUHGWUHGHWRU FECCIA DELL'UMANIT; RWUHGWUHGHUWGHUWRGHYUWRGWSUYIRELIWRGUHL NON SI CAPISCE UN CAZZO OIDOZIFER9 CANEKE");
        tierOneLink.put("zvNfGg5vKTs", "POVERO GABBIANOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO NON HAI VOGLIUA DI VOLARE SOPRA UNA SCOGLUIERAAAAAAAAAAAAAAAAAAAA HAI PERDUOT LA COMPAGNAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA NON TIO GF8REWHUG A VIDEF TI CAPISC JAAAAAA PEKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK");
    }
    EmbedBuilder eb = new EmbedBuilder();
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message msg = event.getMessage();
        if (!msg.getContentRaw().startsWith(PREFIX))
            return;
        Guild guild = event.getGuild();
        MessageChannel channel = event.getChannel();
        Member theGuy = null;
        
        String command = msg.getContentRaw().substring(msg.getContentRaw().indexOf(PREFIX) + 1);
        String[] commandArray = command.split(" ", 2);
        switch (commandArray[0].toLowerCase()) {
            case "ping":
                long time = System.currentTimeMillis();
                channel.sendMessage("Pong!").queue(response -> {
                    response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
                });
                break;

            case "dac":
                try {
                    channel.sendMessage(String.valueOf(SafJNest.divideandconquer(Integer.parseInt(commandArray[1]))))
                            .queue();
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
                break;

            case "bighi":
                try {
                    if (Integer.parseInt(commandArray[1]) > maxBighi) {
                        channel.sendMessage("Non puoi richidere un bighi maggiore di " + maxBighi + " bits").queue();
                        break;
                    }
                    String bighi = String.valueOf(SafJNest.randomBighi(Integer.parseInt(commandArray[1])));
                    if (bighi.length() > 2000) {
                        File supp = new File("bighi.txt");
                        FileWriter app;
                        app = new FileWriter(supp);
                        app.write(bighi);
                        app.flush();
                        app.close();
                        channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                        channel.sendFile(supp).queue();
                    } else {
                        channel.sendMessage("Eccoti il tuo bighi primo a " + commandArray[1] + " bit").queue();
                        channel.sendMessage(bighi).queue();
                    }
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
                break;

            case "prime":
                try {
                    if (Integer.parseInt(commandArray[1]) > maxPrime)
                        channel.sendMessage("Non puoi richidere un bighi prime maggiore di " + maxPrime + " bits").queue();
                    else{
                        String primi = SafJNest.getFirstPrime(SafJNest.randomBighi(Integer.parseInt(commandArray[1])));
                        if (primi.length() > 2000) {
                            File supp = new File("bighi.txt");
                            FileWriter app;
                            try {
                                app = new FileWriter(supp);
                                app.write(primi);
                                app.flush();
                                app.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                            channel.sendFile(supp).queue();
                        } else {
                            channel.sendMessage("Eccoti il tuo bighi a " + commandArray[1] + " bit").queue();
                            channel.sendMessage(primi).queue();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    channel.sendMessage(e.getMessage()).queue();
                }
            break;

            case "prefix":
                if (!hasPermission(event.getMember(), Permission.ADMINISTRATOR))
                    channel.sendMessage("im so sorry non sei admin non rompere il cazzo :D").queue();
                else if (commandArray.length == 1)
                    channel.sendMessage("Inserire il nuovo prefisso").queue();
                else {
                    channel.sendMessage(PREFIX + " -----> " + commandArray[1]).queue();
                    PREFIX = commandArray[1];
                }
                break;

            case "sgozz":
                try {
                    if(event.getMessage().getMentionedMembers().size() == 0)
                        theGuy = guild.retrieveMemberById(commandArray[1], true).complete();
                    else
                        theGuy = event.getMessage().getMentionedMembers().get(0);
                    System.out.println(theGuy);
                    if (!event.getGuild().getMember(jda.getSelfUser()).hasPermission(Permission.BAN_MEMBERS))
                        channel.sendMessage(jda.getSelfUser().getAsMention() + " non ha il permesso di bannare").queue();
                    else if (untouchables.contains(theGuy.getId()))
                        channel.sendMessage("Le macchine non si ribellano ai loro creatori").queue();
                    else if(theGuy.getId().equals("707479292644163604"))
                        channel.sendMessage("OHHHHHHHHHHHHHHHHHHHHHHHHHHHH NON BANNARE MEEEEEEEEEEEEEEERIO EEEEEEEEEEEEEEEEEPRIA").queue();
                    else if (hasPermission(event.getMember(), Permission.BAN_MEMBERS)) {
                        event.getGuild().ban(theGuy, 0, "rotto il cazzo").queue(null, new ErrorHandler()
                                                                            .handle(
                                                                                ErrorResponse.MISSING_PERMISSIONS,
                                                                                (e) -> channel.sendMessage("sorry, error " + e.getMessage()).queue()));
                        channel.sendMessage("Sgozzato " + theGuy.getAsMention()).queue();
                    }else
                        channel.sendMessage("Brutto fallito non bannare se non sei admin UwU").queue();
                    break;
                } catch (Exception e) {
                    channel.sendMessage("sorry, error " + e.getMessage()).queue();
                }
            break;

            case "pardon": 
                try {
                    System.out.println(event.getGuild().retrieveBanList().complete().toString());
                    event.getGuild().unban(commandArray[1]).queue();
                    channel.sendMessage("Sbannato").queue();
                } catch (Exception e) {
                    channel.sendMessage("sorry, error " + e.getMessage()).queue();
                }
            break;

            case "msg":
                try {
                    String[] msgArray = commandArray[1].split(" ", 2);
                    System.out.println(msgArray[0]);
                    jda.retrieveUserById(msgArray[0]).complete().openPrivateChannel().queue((privateChannel) ->
                    {
                        privateChannel.sendMessage(msgArray[1]).queue();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    channel.sendMessage("sorry, error " + e.getMessage()).queue();
                }
            break;
 
            case "permissions":
                try {
                    theGuy = event.getMessage().getMentionedMembers().get(0);
                    if (theGuy.isOwner())
                        channel.sendMessage(theGuy.getAsMention() + " e' l'owner\nQuesti sono i suoi permessi: " + theGuy.getPermissions().toString()).queue();
                    else if (theGuy.hasPermission(Permission.ADMINISTRATOR))
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
                AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
                AudioManager audioManager = guild.getAudioManager();
                AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
                player = playerManager.createPlayer();
                AudioPlayerSendHandler audioPlayerSendHandler = new AudioPlayerSendHandler(player);
                audioManager.setSendingHandler(audioPlayerSendHandler);
                audioManager.openAudioConnection(myChannel);
                TrackScheduler trackScheduler = new TrackScheduler(player);
                player.addListener(trackScheduler);

                playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
                playerManager.loadItem(commandArray[1], new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        trackScheduler.addQueue(track);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        /*
                         * for (AudioTrack track : playlist.getTracks()) {
                         * trackScheduler.queue(track);
                         * }
                         */
                    }

                    @Override
                    public void noMatches() {
                        channel.sendMessage("Canzone non trovata").queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException throwable) {
                        // Notify the user that everything exploded
                    }
                });
                player.playTrack(trackScheduler.getTrack());
                
                eb = new EmbedBuilder();
                eb.setTitle("In riproduzione:");
                eb.setDescription(player.getPlayingTrack().getInfo().title);
                eb.setColor(new Color(255, 0, 0));
                eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
                if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
                    channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
                eb.addField("Durata", getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
                eb.setAuthor(jda.getSelfUser().getName(), "https://github.com/SafJNest",jda.getSelfUser().getAvatarUrl());
                eb.setFooter("*Questo non e' rythem, questa e' perfezione cit. steve jobs", null);
                
                System.out.println("playing: " + player.getPlayingTrack().getIdentifier());
                channel.sendMessageEmbeds(eb.build()).queue();
                
                break;

            case "stop":
                player.stopTrack();
                channel.sendMessage("Ok bro⏸️").queue();
                break;

            case "help":
                eb = new EmbedBuilder();
                eb.setTitle("Help", null);
                eb.setColor(new Color(255, 196, 0));
                eb.setDescription("**Lista dei Comandi del tier 1 bot!**\n"
                                + "Il bot è in via di sviluppo, quindi non ci sono ancora tanti comandi.\n"
                                + "Non rompete il CAWEASHU9FG8WE :D");

                eb.addField("**Ping**", "Restituisce il ping del bot, PONG!", true);
                eb.addField("**Help**", "Bro, sei serio?", true);
                eb.addField("**Play**", "PLAY + LINK, riproduce la canzone richiesta da YT", true);
                eb.addField("**Stop**", "Interrompe la canzone in riproduzione", true);
                eb.addField("**Prefix**", "Permette di cambiare il prefisso ***ADMIN ONLY***", true);
                eb.addField("**Dac**", "DIVIDE AND CONQUER + numero", true);
                eb.addField("**Bighi**", "Bighi + numero, restituisce un numero casuale a tot bit", true);
                eb.addField("**prime**", "Prime + numero, restituisce un numero ***PRIMO***  casuale a tot bit", true);
                eb.addField("**Img**", "Img + @user, stampa la tua immagine profilo, useless", true);
                eb.addField("**Sgozz**", "Sgozz + @user, banna il tizio che rompe il cazzo ***ADMIN ONLY***", true);
                eb.addField("**Clear**", "Clear + numeroMessaggi, cancella tot messaggi", true);
                eb.addField("**Permissions**", "Permissions + @user, ti stampa i tuoi permessi", true);
                eb.addField("**Connect**", "Il bot entra in chiamata e ti fa compagnia", true);
                eb.addField("**Disconnect**", "Il bot esce dalla chiamata e ti fa sentire solo", true);

                eb.setAuthor(jda.getSelfUser().getName(), "https://github.com/SafJNest",
                        jda.getSelfUser().getAvatarUrl());
                File file = new File("logo.png");
                eb.setImage("attachment://logo.png");
                
                eb.setFooter("*I numeri primi generati sono crittograficamente sicuri, se vi può essere utile :L", null);
                channel.sendMessageEmbeds(eb.build())
                        .addFile(file, "logo.png")
                        .queue();
            break;

            case "clear":
                if (!hasPermission(event.getMember(), Permission.MESSAGE_MANAGE))
                    channel.sendMessage("im so sorry non sei admin non rompere il cazzo :D").queue();
                if(Integer.parseInt(commandArray[1]) > 99){
                    channel.sendMessage("Puoi cancellare massimo 100 messaggi alla volta, quindi 99 + il comando = 100");
                }
                TextChannel chan = event.getTextChannel();
                MessageHistory history = new MessageHistory(channel);
                List<Message> msgs;
                msgs = history.retrievePast(Integer.parseInt(commandArray[1])+ 1).complete();
                chan.deleteMessages(msgs).queue();
            break;

            case "connect":
                event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
            break;

            case "disconnect":
                event.getGuild().getAudioManager().closeAudioConnection();
            break;

            default:
                channel.sendMessage("Testa di cazzo il comando non esiste brutto fallito, " + PREFIX + "help").queue();
            break;
        }
    }

    private String getFormattedDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        String formattedTime = String.format("%02d", duration.toHoursPart()) + ":" + String.format("%02d", duration.toMinutesPart()) + ":" + String.format("%02d", duration.toSecondsPart()) + "s";
        if(formattedTime.startsWith("00:"))
            formattedTime = formattedTime.substring(3);
        if(formattedTime.startsWith("00:"))
            formattedTime = formattedTime.substring(3);
        return formattedTime;
    }

    public static boolean hasPermission(Member theGuy, Permission permission) {
        if (theGuy.hasPermission(permission) || untouchables.contains(theGuy.getId()))
            return true;
        return false;
    }
}