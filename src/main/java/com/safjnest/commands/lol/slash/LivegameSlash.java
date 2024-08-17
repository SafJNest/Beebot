package com.safjnest.commands.lol.slash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.lol.Livegame;
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
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class LivegameSlash extends SlashCommand {


    public LivegameSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
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
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        Button left = Button.primary("rank-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));;
        Button right = Button.primary("rank-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));;
        Button center = Button.primary("rank-center", "f");
        Button refresh = Button.primary("rank-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        event.deferReply(false).queue();

        User theGuy = null;
        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        center = Button.primary("rank-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();

        LeagueShard shard = s.getPlatform();
        
        List<SpectatorParticipant> users = null;
        List<RiotAccount> accounts = new ArrayList<>();

        accounts.add(account);
        
        EmbedBuilder builder = null;
        try {
            users = s.getCurrentGame().getParticipants();
        
            StringSelectMenu menu = Livegame.createSummonersMenu(users, accounts, shard);

            builder = Livegame.createEmbed(s, users, accounts);

            if (theGuy != null && LeagueHandler.getNumberOfProfile(event.getMember().getId()) > 1) {
                WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
                        action.setComponents(ActionRow.of(menu),
                                            ActionRow.of(left, center, right, refresh)).queue();
                return;
            }

            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
            action.setComponents(ActionRow.of(menu), ActionRow.of(center, refresh)).queue();
                
        } catch (Exception e) {
            builder = Livegame.createEmbed(s, users, accounts);
            if (theGuy != null && LeagueHandler.getNumberOfProfile(event.getMember().getId()) > 1) {
                WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
                        action.setComponents(ActionRow.of(left, center, right, refresh))
                        .queue();
                return;
            }
            event.getHook().editOriginalEmbeds(builder.build()).setComponents(ActionRow.of(center, refresh)).queue();
        }
	}
}
