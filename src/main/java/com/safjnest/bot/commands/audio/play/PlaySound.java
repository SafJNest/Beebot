package com.safjnest.bot.commands.audio.play;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.bot.audio.SoundEmbed;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;


import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class PlaySound extends SlashCommand{
    
    private PlayerManager pm;

    public PlaySound(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to play", true)
                .setAutoComplete(true)
        );
        commandData.setThings(this);
        this.pm = PlayerManager.get();
    }

    public PlaySound(){
        this.name = this.getClass().getSimpleName().toLowerCase().replace("slash", "");

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.pm = PlayerManager.get();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        AudioChannel authorChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        String fileName = event.getOption("sound").getAsString();

        if(authorChannel == null){
            event.deferReply().addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (authorChannel != botChannel)){
            event.deferReply().addContent("The bot is already being used in another voice channel.").queue();
            return;
        }
        
        Sound sound = SoundCache.getSoundByString(fileName, guild, event.getUser());

        if(sound == null) {
            event.deferReply().addContent("Couldn't find a sound with that name/id. You can upload a new one using /upload").queue();
            return;
        }

        event.deferReply(false).queue(hook -> pm.loadSound(guild, sound, new ResultHandler(event, hook, sound)));
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        AudioChannel authorChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        String fileName;
        if((fileName = event.getArgs()).equals("")){
            event.reply("You need to specify a sound name or id.");
            return;
        }

        if(authorChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && (authorChannel != botChannel)){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }
        
        Sound sound = SoundCache.getSoundByString(fileName, guild, event.getAuthor());

        if(sound == null) {
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

        pm.loadSound(guild, sound, new ResultHandler(event, sound));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final InteractionHook hook;
        private final CommandEvent commandEvent;
        private final Guild guild;
        private final Member author;
        private final Sound sound;
        
        private ResultHandler(SlashCommandEvent event, InteractionHook hook, Sound sound) {
            this.hook = hook;
            this.commandEvent = null;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.sound = sound;
        }

        private ResultHandler(CommandEvent event, Sound sound) {
            this.commandEvent = event;
            this.hook = null;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.sound = sound;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            pm.getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.SOUND);

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

            Sound played = SoundCache.withSound(sound.getId(), current -> {
                current.increaseUserPlays(author.getId(), guild.getId());

                return current;
            });

            if (commandEvent != null) 
                commandEvent.getChannel().sendMessageEmbeds(SoundEmbed.getSoundEmbed(played, author.getUser()).build()).setComponents(SoundEmbed.getSoundEmbedButtons(played)).queue();
            else 
                hook.editOriginalEmbeds(SoundEmbed.getSoundEmbed(played, author.getUser()).build()).setComponents(SoundEmbed.getSoundEmbedButtons(played)).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {}

        @Override
        public void noMatches() {
            reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            reply(throwable.getMessage());
        }

        private void reply(String message) {
            if(commandEvent != null) commandEvent.reply(message);
            else hook.editOriginal(message).queue();
        }

    }
}
