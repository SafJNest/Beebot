package com.safjnest.commands.queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.ResultHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public class PlayYoutube extends Command {
    private PlayerManager pm;

    public PlayYoutube(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
        
        this.pm = PlayerManager.get();
    }

	@Override
	protected void execute(CommandEvent event) {
        String search = event.getArgs();
        Guild guild = event.getGuild();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        if(myChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && myChannel != botChannel){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }

        pm.loadItemOrdered(guild, search, new ResultHandler(event, false, false));
    }
}
