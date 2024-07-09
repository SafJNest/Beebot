package com.safjnest.commands.League.slash;

import java.util.Arrays;
    
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.League.Summoner;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerInfoSlash extends SlashCommand {
 
    public SummonerInfoSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to get information on", false),
            RiotHandler.getLeagueShardOptions(),
            new OptionData(OptionType.USER, "user", "Discord user you want to get information on (if riot account is connected)", false)
        );
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        Button left = Button.primary("lol-left", "<-");
        Button right = Button.primary("lol-right", "->");
        Button center = Button.primary("lol-center", "f");
        
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        event.deferReply(false).queue();

        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }
        
        EmbedBuilder builder = Summoner.createEmbed(event.getJDA(),event.getJDA().getSelfUser().getId(), s);
        
        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("lol-center-" + s.getPUUID() + "#" + s.getPlatform().commonName(), account.getName());
            center = center.asDisabled();

            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(),s).build());
            action.setComponents(ActionRow.of(left, center, right)).queue();
            return;
        }

        event.getHook().editOriginalEmbeds(builder.build()).queue();
        

	}

}
