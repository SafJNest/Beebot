//TODO RIFARE QUESTA CLASSE DI MERDA INGUARADFBILE :D
package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.amazonaws.services.s3.model.S3Object;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.TrackScheduler;
import com.safjnest.Utilities.AudioHandler;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PostgreSQL;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.SoundBoard;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.utils.FileUpload;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;

public class PlaySound extends Command{
    PostgreSQL sql;
    AwsS3 s3Client;
    String fileName;


    public PlaySound(AwsS3 s3Client, PostgreSQL sql){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.s3Client = s3Client;
        this.sql = sql;
    }

    @Override
    protected void execute(CommandEvent event) {
        if((fileName = event.getArgs()) == ""){
            event.reply("Missing name");
            return;
        }
        
        File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        if(!soundBoard.exists())
            soundBoard.mkdirs();

        //TODO fix | deletare il file vecchio ogni ps bene
        for (File file : soundBoard.listFiles())
            file.delete();

        //String query = "SELECT id, name, guild_id FROM sound WHERE name = '" + fileName + "' AND guild_id = '" + event.getGuild().getId() + "';";
        String query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE name = '" + fileName + "';";
        String id = null, name, guildId, userId, extension;
        ArrayList<ArrayList<String>> arr = sql.getTuple(query, 5); //qualcuno ha visto gesù, dentro al parcheggio della pizzeria
        int indexForKeria = -1;
        for(int i = 0; i < arr.size(); i++){
            if(arr.get(i).get(2).equals(event.getGuild().getId())){
               indexForKeria = i;
               break;
            }
        }
        
        if(indexForKeria == -1){
            indexForKeria = (int)(Math.random()*arr.size());
        }

        id = arr.get(indexForKeria).get(0);
        name = arr.get(indexForKeria).get(1);
        guildId = arr.get(indexForKeria).get(2);
        userId = arr.get(indexForKeria).get(3);
        extension = arr.get(indexForKeria).get(4);

        S3Object sound = s3Client.downloadFile(id, event);
        
        if(sound == null){
            event.reply("sound not found in aws s3");
            return;
        }
        
        fileName = "rsc" + File.separator + "SoundBoard" + File.separator + id +"."+ extension;
        
        MessageChannel channel = event.getChannel();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioHandler audioPlayerSendHandler = new AudioHandler(player);

        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);
        
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        playerManager.loadItem(fileName, new AudioLoadResultHandler() {
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
                channel.sendMessage("File not found").queue();
                trackScheduler.addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                System.out.println("error: " + throwable.getMessage());
            }
        });

        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;

        //String query;
        query = "SELECT times FROM play join sound on play.id_sound = sound.id where play.id_sound = '"+id+"' and play.user_id = '"+userId+"';";
        if(sql.getString(query, "times") == null ){
            query = "INSERT INTO play(user_id, id_sound, times) VALUES('"+userId+"','"+id+"', 1);";        
        }else{
            query = "UPDATE play SET times = times + 1 WHERE id_sound = (" + id+ ") AND user_id = '" +userId+"';";
        }
        sql.runQuery(query);
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Playing now:");

        eb.setDescription("```" + event.getArgs() + "```");

        eb.addField("Author", "```" + event.getJDA().getUserById(sound.getObjectMetadata().getUserMetaDataOf("author")).getName() + "```", true);
        try {
            eb.addField("Lenght","```" + (extension.equals("opus") 
            ? SafJNest.getFormattedDuration((Math.round(SoundBoard.getOpusDuration(fileName)))*1000)
            : SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length)) + "```", true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        eb.addBlankField(true);

        eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest",event.getAuthor().getAvatarUrl());
        eb.setFooter("*This is not SoundFx, this is much worse cit. steve jobs (probably)", null); //Questo non e' SoundFx, questa e' perfezione cit. steve jobs (probabilmente)
        //Mp3File mp = SoundBoard.getMp3FileByName(player.getPlayingTrack().getInfo().title);

        
        eb.addField("Guild", "```" + event.getJDA().getGuildById(sound.getObjectMetadata().getUserMetaDataOf("guild")).getName() + "```", true);
        query = "SELECT SUM(times) FROM PLAY where id_sound='" + id + "';";

        String playedTimes = sql.getString(query, "sum");
        eb.addField("Played", "```" + playedTimes + (playedTimes.equals("1") ? " time" : " times") + "```", true);

        String img = "idk";
        if(extension.equals("opus")){
            eb.setColor(new Color(255, 0, 0));
            img = "opus.png";
        }else{
            img = "mp3.png";
    	     eb.setColor(new Color(0, 255, 255));
        }   
            

        File file = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        channel.sendMessageEmbeds(eb.build())
        .addFiles(FileUpload.fromData(file))
            .queue();
    }
}
