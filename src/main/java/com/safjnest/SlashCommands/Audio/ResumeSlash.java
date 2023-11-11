package com.safjnest.SlashCommands.Audio;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerPool;


public class ResumeSlash extends SlashCommand {

    public ResumeSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        PlayerPool.get(event.getJDA().getSelfUser().getId(), event.getGuild().getId()).getPlayer().setPaused(false);

        event.deferReply(false).addContent("Playing resumed").queue();
    }
}
