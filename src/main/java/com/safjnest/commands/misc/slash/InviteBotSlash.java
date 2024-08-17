package com.safjnest.commands.misc.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class InviteBotSlash extends SlashCommand{

    public InviteBotSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(false).addContent(event.getJDA().getInviteUrl()).queue();
    }

}
