package com.safjnest.commands.Audio.slash.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.model.Sound;
import com.safjnest.model.Sound.Tag;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 8.0
 */
public class SearchSoundSlash extends SlashCommand {

    public SearchSoundSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "query", "What are you looking for", true),
            new OptionData(OptionType.USER, "author", "Who uploaded the sound", false)
        );
    }

   

	@Override
	protected void execute(SlashCommandEvent event) {
        String query = event.getOption("query").getAsString();
        String author = event.getOption("author") == null ? null : event.getOption("author").getAsUser().getId();

        QueryResult result = author == null ? DatabaseHandler.extremeSoundResearch(query) : DatabaseHandler.extremeSoundResearch(query, author);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor("Search by" + event.getUser().getName(), "https://discord.com/users/" + event.getUser().getId(), event.getUser().getEffectiveAvatarUrl());
        eb.setTitle("Search for: " + query);

        if (result.isEmpty()) {
            eb.setDescription("No results found");
            eb.setColor(Bot.getColor());
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        int count = 1;
        StringBuilder field1 = new StringBuilder();
        StringBuilder field2 = new StringBuilder();
        
        for (ResultRow sound : result) {
            if (count <= 15) {
                field1.append("`").append(count).append("` ").append(sound.get("name")).append("\n");
            } else if (count <= 30) {
                field2.append("`").append(count).append("` ").append(sound.get("name")).append("\n");
            }
            count++;
        }
        
        eb.addField(" ", field1.toString(), true);
        if (field2.length() > 0) {
            eb.addBlankField(true);
            eb.addField(" ", field2.toString(), true);
        }

        eb.setColor(Bot.getColor());

        String menuId = "menu:" + event.getUser().getId() + "-" + SafJNest.getJSalt(4);
        StringSelectMenu.Builder mb = StringSelectMenu.create(menuId);

        count = 1;
        for (ResultRow sound : result) {
            String label = count + " - " + sound.get("name");
            label = PermissionHandler.ellipsis(label, 100);
            mb.addOption(label, sound.get("id"));
            count++;
        }
        MenuListener fileListener = new MenuListener(event, mb.getId());
        event.getJDA().addEventListener(fileListener);

        event.replyEmbeds(eb.build())
             .addComponents(ActionRow.of(mb.build())).queue();

        
    } 

    private class MenuListener extends ListenerAdapter {
        
        private SlashCommandEvent slashEvent;
        private String menuID;
        private StringSelectInteractionEvent menuEvent;
    
        public MenuListener(SlashCommandEvent slashEvent, String menuID) {
            this.slashEvent = slashEvent;
            this.menuID = menuID;
        }
    
        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event) {
            if (event.getComponentId().equals(menuID)) {
                Guild guild = event.getGuild();
                this.menuEvent = event;
            
                AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
                AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
    
                if(myChannel == null){
                    event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
                    return;
                }
        
                if(botChannel != null && (myChannel != botChannel)){
                    event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
                    return;
                }
    
                List<String> selected = event.getValues();
                Sound sound = SoundHandler.getSoundById(selected.get(0));
    
                String fileName = sound.getPath();
                PlayerManager.get().loadItemOrdered(guild, fileName, new ResultHandler(slashEvent, sound, fileName));
                
            }
        }
    
        private class ResultHandler implements AudioLoadResultHandler {
            private final SlashCommandEvent event;
            private final Guild guild;
            private final Member author;
            private final Sound sound;
            private final String fileName;
            
            private ResultHandler(SlashCommandEvent event, Sound sound, String fileName) {
                this.event = event;
                this.guild = event.getGuild();
                this.author = event.getMember();
                this.sound = sound;
                this.fileName = fileName;
            }
            
            @Override
            public void trackLoaded(AudioTrack track) {
                PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.SOUND);
    
                guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());
    
                sound.increaseUserPlays(author.getId());
    
                EmbedBuilder eb = new EmbedBuilder();
                eb.setAuthor(author.getEffectiveName(), "https://github.com/SafJNest", author.getEffectiveAvatarUrl());
                eb.setTitle("Playing now:");
                eb.setDescription("```" + sound.getName() + " (ID: " + sound.getId() + ") " + ((sound.isPublic()) ? ":public:" : ":private:") + "```");
                eb.setColor(Bot.getColor());
                eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
    
                eb.addField("Author", "```" 
                    + event.getJDA().getUserById(sound.getUserId()).getName() 
                + "```", true);
    
                try {
                    eb.addField("Lenght", "```"
                        + (sound.isOpus()
                        ? SafJNest.getFormattedDuration((Math.round(SoundHandler.getOpusDuration(fileName)))*1000)
                        : SafJNest.getFormattedDuration(track.getInfo().length))
                    + "```", true);
                } catch (IOException e) {e.printStackTrace();}
    
                eb.addField("Format", "```" 
                    + sound.getExtension().toUpperCase() 
                + "```", true);
    
                eb.addField("Guild", "```" 
                    + event.getJDA().getGuildById(sound.getGuildId()).getName() 
                + "```", true);
    
                int[] plays = sound.getPlays(author.getId());
                eb.addField("Played", "```" 
                    + plays[0]
                    + (plays[0] == 1 ? " time" : " times") 
                    + " (yours: "+ plays[1] + ")"
                + "```", true);
    
                Tag[] tags = sound.getTags();
                StringBuilder tagList = new StringBuilder();
                for(Tag tag : tags) {
                    if (!tag.getName().isBlank()) tagList.append(tag.getName()).append(", ");
                }
    
    
                eb.addField("Tags", "```"
                    + tagList.toString().substring(0, tagList.length() - 2)
                    + "```", false);
    
                eb.addField("Creation time", 
                    "<t:" + sound.getTimestampSecond() + ":f>"  + " | <t:" + sound.getTimestampSecond() + ":R>",
                false);
                
                menuEvent.deferEdit().queue();
                menuEvent.getMessage().delete().queue();
                menuEvent.getChannel().sendMessageEmbeds(eb.build()).queue();
            }
    
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {}
    
            @Override
            public void noMatches() {
                event.reply("No matches");
            }
    
            @Override
            public void loadFailed(FriendlyException throwable) {
                event.reply(throwable.getMessage());
            }
        }
    }
}
