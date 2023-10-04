package com.safjnest.SlashCommands.Audio.Play;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.SoundBoard;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.SQL;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

public class PlaySoundSlash extends SlashCommand{
    SQL sql;
    String path = "rsc" + File.separator + "SoundBoard"+ File.separator;
    String fileName;
    PlayerManager pm;


    public PlaySoundSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.sql = DatabaseHandler.getSql();
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to play", true)
                            .setAutoComplete(true));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();
        
        if(myChannel == null){
            event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
            return;
        }

        fileName = event.getOption("sound").getAsString();
        
        File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        if(!soundBoard.exists())
            soundBoard.mkdirs();


        String query = null;
        String id = null, name, guildId, userId, extension;
        ArrayList<ArrayList<String>> arr = null;

        
        if(fileName.matches("[0123456789]*")){
            query = "SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE id = '" + fileName + "' AND  (guild_id = '" + event.getGuild().getId() + "'  OR public = 1 OR user_id = '" + event.getMember().getId() + "')";
        }
        else{
            query = "SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE name = '" + fileName + "' AND (guild_id = '" + event.getGuild().getId() + "'  OR public = 1 OR user_id = '" + event.getMember().getId() + "')";
        }


        if((arr = sql.getAllRows(query, 6)).isEmpty()){
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

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

        
        fileName = path + id + "." + extension;

        pm = new PlayerManager();
        
        AudioManager audioManager = event.getGuild().getAudioManager();
        audioManager.setSendingHandler(pm.getAudioHandler());
        audioManager.openAudioConnection(myChannel);

        if(pm.getPlayer().getPlayingTrack() != null){
            //pm.stopAudioHandler();
        }
        
        pm.getAudioPlayerManager().loadItem(fileName, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                pm.getTrackScheduler().addQueue(track);
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
                event.deferReply(true).addContent("Not found").queue();
                pm.getTrackScheduler().addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                event.deferReply(true).addContent(throwable.getMessage()).queue();
            }
        });

        pm.getPlayer().playTrack(pm.getTrackScheduler().getTrack());
        if(pm.getPlayer().getPlayingTrack() == null) {
            return;
        }
        

        query = "SELECT times FROM play where play.sound_id = '" + id + "' and play.user_id = '" + event.getMember().getId() + "';";
        if(sql.getString(query, "times") == null){
            query = "INSERT INTO play(user_id, sound_id, times) VALUES('" + event.getMember().getId() + "','" + id + "', 1);";
        }
        else{
            query = "UPDATE play SET times = times + 1 WHERE sound_id = (" + id + ") AND user_id = '" + event.getMember().getId() + "';";
        }
        
        sql.runQuery(query);
        query = "SELECT SUM(times) as sum FROM play where sound_id='" + id + "';";
        String timesPlayed = sql.getString(query, "sum");
        query = "SELECT times FROM play where sound_id='" + id + "' AND user_id='"+event.getMember().getId()+"';";
        String timesPlayedByUser = sql.getString(query, "times");

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor(event.getMember().getEffectiveName(), "https://github.com/SafJNest", event.getMember().getAvatarUrl());

        eb.setTitle("Playing now:");
        
        String locket = (arr.get(indexForKeria).get(5).equals("1")) ? ":public:" : ":private:";
        eb.setDescription("```" + name + " (ID: " + id + ") "+ locket + "```");

        eb.addField("Author", "```" + event.getJDA().getUserById(userId).getName() + "```", true);
        try {
            eb.addField("Lenght","```" + (extension.equals("opus") 
            ? SafJNest.getFormattedDuration((Math.round(SoundBoard.getOpusDuration(fileName)))*1000)
            : SafJNest.getFormattedDuration(pm.getPlayer().getPlayingTrack().getInfo().length)) + "```", true);
        } catch (IOException e) {e.printStackTrace();}
        

        //Mp3File mp = SoundBoard.getMp3FileByName(player.getPlayingTrack().getInfo().title);
        eb.addField("Format", "```"+extension.toUpperCase()+"```", true);

        eb.addField("Guild", "```" + event.getJDA().getGuildById(guildId).getName() + "```", true);

        eb.addField("Played", "```" + timesPlayed + (timesPlayed.equals("1") ? " time" : " times") + " (yours: "+timesPlayedByUser+")```", true);

         
        eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
            
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        event.deferReply(false).addEmbeds(eb.build()).queue();
    }
}
