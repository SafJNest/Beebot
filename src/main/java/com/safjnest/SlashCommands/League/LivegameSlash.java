package com.safjnest.SlashCommands.League;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Commands.League.Livegame;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class LivegameSlash extends SlashCommand {


    public LivegameSlash(){
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

	@Override
	protected void execute(SlashCommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        Button left = Button.primary("rank-left", "<-");
        Button right = Button.primary("rank-right", "->");
        Button center = Button.primary("rank-center", "f");
        event.deferReply(false).queue();

        User theGuy = null;
        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }

        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("rank-center-" + s.getPUUID() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();
        }

        LeagueShard shard = s.getPlatform();
        RegionShard region = RiotHandler.getRegionFromServer(shard);
        
        List<SpectatorParticipant> users = null;
        List<RiotAccount> accounts = new ArrayList<>();
        
        EmbedBuilder builder = null;
        try {
            users = s.getCurrentGame().getParticipants();
        
            ArrayList<SelectOption> options = new ArrayList<>();
            for(SpectatorParticipant p : users){
                RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(region, p.getPuuid());
                accounts.add(account);

                Emoji icon = Emoji.fromCustom(
                    RiotHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName(), 
                    Long.parseLong(RiotHandler.getEmojiId(event.getJDA(), RiotHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName())), 
                    false);
                if(!p.getSummonerId().equals(s.getSummonerId()))
                    options.add(SelectOption.of(
                                    account.getName().toUpperCase(), 
                                    p.getSummonerId() + "#" + s.getPlatform().name()).withEmoji(icon));
            }

            StringSelectMenu menu = StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();

            builder = Livegame.createEmbed(event.getJDA(), event.getMember().getId(), s, users, accounts);

            if (theGuy != null && RiotHandler.getNumberOfProfile(event.getMember().getId()) > 1) {
                WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
                        action.setComponents(ActionRow.of(menu),
                                            ActionRow.of(left, center, right)).queue();
                return;
            }
            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
            action.setComponents(ActionRow.of(menu)).queue();
                
        } catch (Exception e) {
            builder = Livegame.createEmbed(event.getJDA(), event.getMember().getId(), s, users, accounts);
            if (theGuy != null && RiotHandler.getNumberOfProfile(event.getMember().getId()) > 1) {
                WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(builder.build());
                        action.setComponents(ActionRow.of(left, center, right))
                        .queue();
                return;
            }
            event.getHook().editOriginalEmbeds(builder.build()).queue();
        }
	}
}
