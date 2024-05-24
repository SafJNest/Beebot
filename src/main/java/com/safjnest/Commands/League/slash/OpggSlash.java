package com.safjnest.commands.League.slash;


import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.League.Opgg;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
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
public class OpggSlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public OpggSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name of the summoner you want to get information on", false),
            new OptionData(OptionType.STRING, "tag", "Tag of the summoner you want to get information on", false),
            RiotHandler.getLeagueShardOptions(),
            new OptionData(OptionType.USER, "user", "Discord user you want to get information on (if riot account is connected)", false));
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        Button left = Button.primary("match-left", "<-");
        Button right = Button.primary("match-right", "->");
        Button center = Button.primary("match-center", "f");

        boolean searchByUser = false;
        
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        event.deferReply(false).queue();

        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }
        
        EmbedBuilder builder = Opgg.createEmbed(s, event.getJDA());
        
        if(searchByUser && RiotHandler.getNumberOfProfile(event.getUser().getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("match-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();
            
            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
            action.setComponents(ActionRow.of(left, center, right)).queue();
            return;
        }

        event.getHook().editOriginalEmbeds(builder.build()).queue();
	}



}
