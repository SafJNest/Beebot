package com.safjnest.commands.ManageGuild.slash;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class ClearSlash extends SlashCommand {

    public ClearSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.botPermissions = new Permission[]{Permission.MESSAGE_MANAGE};
        this.userPermissions = new Permission[]{Permission.MESSAGE_MANAGE};
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "value", "Number of messages to delete (max 100)", true)
                .setMinValue(2)
                .setMaxValue(100)
        );    
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        int value = event.getOption("value").getAsInt();
        
        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(value).complete();
        event.getTextChannel().deleteMessages(msgs).queue();

        event.deferReply(true).addContent(value + " messages deleted.").queue();
	}
}