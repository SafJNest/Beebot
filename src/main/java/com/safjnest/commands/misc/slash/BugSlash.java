package com.safjnest.commands.misc.slash;

import java.util.Arrays;

import com.safjnest.core.Bot;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * This command let the user send a message to the {@link com.safjnest.util.PermissionHandler#untouchables developers}
 * about a bug that occurs with a command.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class BugSlash extends SlashCommand {

    public BugSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "command", "Name of the bugged command", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "text", "Describe the bug", true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("BUGS ALERT "+event.getOption("command").getAsString());
        eb.setAuthor(event.getUser().getName() + " from " + event.getGuild().getName());
        eb.setThumbnail(event.getUser().getAvatarUrl());
        eb.setDescription(event.getOption("text").getAsString());
        eb.setColor(Bot.getColor());

        PermissionHandler.getUntouchables().forEach((id) -> event.getJDA().retrieveUserById(id).complete().openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(eb.build()).queue()));
        event.deferReply(true).addContent("Message sent successfuly").queue();
    }
}