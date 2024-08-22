package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Livegame extends Command {
    /**
     * Constructor
     */
    public Livegame() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(CommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        User theGuy = null;

        if(event.getArgs().equals("")) theGuy = event.getAuthor();    
        else if(event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }
        
        List<SpectatorParticipant> users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;
        List<RiotAccount> accounts = new ArrayList<>();

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        accounts.add(account);


        StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users, accounts);
        EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users, accounts);
        List<LayoutComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, theGuy != null ? theGuy.getId() : null));

        if (menu != null) {
            row.add(0, ActionRow.of(menu));
            event.getChannel().sendMessageEmbeds(builder.build()).setComponents(row).queue();
        }
        else event.getChannel().sendMessageEmbeds(builder.build()).setComponents(row).queue();
        

    }

}
