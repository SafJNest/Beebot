package com.safjnest.commands.lol.slash.summoner;

import java.util.Arrays;
    
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.lol.Summoner;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerInfoSlash extends SlashCommand {
 
    public SummonerInfoSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to get information on", false),
            LeagueHandler.getLeagueShardOptions(),
            new OptionData(OptionType.USER, "user", "Discord user you want to get information on (if riot account is connected)", false)
        );
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        Button left = Button.primary("lol-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("lol-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button center = Button.primary("lol-center", "f");
        Button refresh = Button.primary("lol-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));
        
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        event.deferReply(false).queue();

        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }
        
        EmbedBuilder builder = Summoner.createEmbed(event.getJDA(),event.getJDA().getSelfUser().getId(), s);
        
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        center = Button.primary("lol-center-" + s.getPUUID() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();
        if(theGuy != null && LeagueHandler.getNumberOfProfile(theGuy.getId()) > 1){

            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(),s).build());
            action.setComponents(ActionRow.of(left, center, right, refresh)).queue();
            return;
        }

        event.getHook().editOriginalEmbeds(builder.build()).setComponents(ActionRow.of(center, refresh)).queue();
        

	}

}
