package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.safjnest.Utilities.TrackScheduler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.Utilities.AudioPlayerSendHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Play extends Command {
    private HashMap<String,String> tierOneLink;

    public Play(HashMap<String,String> tierOneLink){
        this.name = "play";
        this.aliases = new String[]{"nuovavita", "p"};
        this.help = "Il bot riproduce canzoni locali o link di youtube.\n"
        + "Bisogna essere connessi in una stanza vocale.\n"
        + "Nel caso il bot sia connesso in un'altra stanza si sposterà nella stessa dove è connesso l'autore del comando.";
        this.tierOneLink = tierOneLink;
        this.category = new Category("Audio");
        this.arguments = "[play] [link/nome suono]";
    }

	@Override
	protected void execute(CommandEvent event) {
        boolean isYoutube = false;
        String toPlay = "";
        if(event.getMember().getVoiceState().getChannel() == null){
            event.reply("Non sei in un canale vocale.");
            return;
        }
        if(event.getArgs().contains("www.youtube.com")){
            isYoutube = true;
            toPlay = event.getArgs();
        }else{
            toPlay = SoundBoard.containsFile(event.getArgs());
            if(toPlay == null){
                event.reply("Suono non trovato");
                return;
            }
            toPlay = "SoundBoard" + File.separator + toPlay; 
        }
        MessageChannel channel = event.getChannel();
        EmbedBuilder eb = new EmbedBuilder();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioPlayerSendHandler audioPlayerSendHandler = new AudioPlayerSendHandler(player);
        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);
        
        playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        
        playerManager.loadItem(toPlay, new AudioLoadResultHandler() {
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
                trackScheduler.addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                System.out.println("error faker " + throwable.getMessage());
            }
        });
        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;
        eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.addField("Durata", SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rhythm, questa e' perfezione cit. steve jobs (probabilmente)", null);
        if(isYoutube){
            eb.setColor(new Color(255, 0, 0));
            eb.setDescription(player.getPlayingTrack().getInfo().title);
            eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
            event.reply(eb.build());
        }
        else{
            Mp3File mp = SoundBoard.getMp3FileByName(player.getPlayingTrack().getInfo().title);
            eb.setColor(new Color(0, 255, 255));
            eb.setDescription(event.getArgs());
            eb.addField("Autore", mp.getId3v2Tag().getAlbumArtist(), true);
            eb.addField("Album", mp.getId3v2Tag().getAlbum(), true);
            String img = "mp3.png";
            switch (mp.getId3v2Tag().getAlbumArtist()) {
                case "merio":
                    img = "epria.jpg";
                    break;
                case "dirix":
                    img = "dirix.jpg";
                    break;
                case "teros":
                    img = "zucca.jpg";
                    break;
                case "herox":
                    img = "herox.jpg";
                    break;
                case "bomber":
                    img = "arcus.jpg";
                    break;
                case "ilyas":
                    img = "maluma.PNG";
                    break;
                case "pyke":
                    img = "pyke.jpg";
                    break;
                case "thresh":
                    img = "thresh.jpg";
                    break;
                case "blitzcrank":
                    img = "blitz.png";
                    break;
                case "bard":
                    img = "bard.png";
                    break;
                case "nautilus":
                    img = "nautilus.png";
                    break;
                case "fiddle":
                    img = "fid.jpg";
                    break;
                case "pantanichi":
                    img = "panta.jpg";
                    break;
                case "sunyx":
                    img = "sun.jpg";
                    break;
                case "gskianto":
                    img = "gk.png";
                    break;
                case "jhin":
                    img = "jhin.jpg";
                    break;
                }
                File file = new File("img" + File.separator+ img);
                eb.setThumbnail("attachment://"+img);
                channel.sendMessageEmbeds(eb.build())
                    .addFile(file, img)
                    .queue();
            }
        
        if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
            channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
	}
}