package com.safjnest.commands.lol.summoner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Summoner extends SlashCommand {
 
    public Summoner(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};


        String father = this.getClass().getSimpleName().replace("Slash", "");
        
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new SummonerLink(father), new SummonerProfile(father), new SummonerUnlink(father), new SummonerTrack(father));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {

	}

    @Override
    protected void execute(CommandEvent event) {
        String args = event.getArgs();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        User theGuy = null;

        if (args.equals("")) theGuy = event.getAuthor();
        else if (event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = LeagueHandler.getSummonerByArgs(event);
        if (s == null) {
            event.reply("Couldn't find the specified summoner. Remember to specify the tag or link an account using `/summoner link`");
            return;
        }

        EmbedBuilder builder = LeagueMessage.getSummonerEmbed(s);
        List<LayoutComponent> buttons = LeagueMessage.getSummonerButtons(s, theGuy != null ? theGuy.getId() : null);

        event.getChannel().sendMessageEmbeds(builder.build()).setComponents(buttons).queue();

    }

}
