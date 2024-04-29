package com.safjnest.Commands.Queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.ResultHandler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public class PlayYoutube extends Command {
    private PlayerManager pm;

    public PlayYoutube(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
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
