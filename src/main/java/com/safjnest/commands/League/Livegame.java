package com.safjnest.commands.League;

import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
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

        Button left = Button.primary("rank-left", "<-");
        Button right = Button.primary("rank-right", "->");
        Button center = Button.primary("rank-center", "f");

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        User theGuy = null;

        if(event.getArgs().equals("")) theGuy = event.getAuthor();    
        else if(event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }
        LeagueShard shard = s.getPlatform();
        RegionShard region = RiotHandler.getRegionFromServer(shard);
        
        List<SpectatorParticipant> users = null;
        List<RiotAccount> accounts = new ArrayList<>();

        RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
        accounts.add(account);
        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            center = Button.primary("rank-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();
        }

        EmbedBuilder builder = null;
        try {
            users = s.getCurrentGame().getParticipants();
        
            ArrayList<SelectOption> options = new ArrayList<>();
            for(SpectatorParticipant p : users){
                account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(region, p.getPuuid());
                accounts.add(account);

                Emoji icon = Emoji.fromCustom(
                    RiotHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName(), 
                    Long.parseLong(CustomEmojiHandler.getEmojiId(RiotHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName())), 
                    false);
                    
                    options.add(SelectOption.of(
                                    account.getName().toUpperCase(), 
                                    p.getSummonerId() + "#" + s.getPlatform().name()).withEmoji(icon));
            }

            StringSelectMenu menu = StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();

            builder = createEmbed(event.getJDA(), event.getAuthor().getId(), s, users, accounts);
            if (theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1) {
                MessageCreateAction action = event.getChannel().sendMessageEmbeds(builder.build());
                action.addComponents(ActionRow.of(menu));
                action.addComponents(ActionRow.of(left, center, right));
                action.queue();
                return;
            }
            event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(menu).queue();
                
        } catch (Exception e) {
            builder = createEmbed(event.getJDA(), event.getAuthor().getId(), s, users, accounts);
            if (theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1) {
                event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(left, center, right).queue();
                return;
            }
            event.getChannel().sendMessageEmbeds(builder.build()).queue();
        }

    }

    public static EmbedBuilder createEmbed(JDA jda, String id, no.stelar7.api.r4j.pojo.lol.summoner.Summoner s, List<SpectatorParticipant> users, List<RiotAccount> accounts) {
        RiotAccount account = null;
        for(RiotAccount a : accounts){
            if(a.getPUUID().equals(s.getPUUID())){
                account = a;
                break;
            }
        }
        try {

            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(account.getName() + "#" + account.getTag() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(RiotHandler.getSummonerProfilePic(s));
            String blueSide = "";
            String redSide = "";
            for (SpectatorParticipant partecipant : users) {
                account = accounts.stream().filter(a -> a.getPUUID().equals(partecipant.getPuuid())).findFirst().orElse(null);


                String sum = CustomEmojiHandler.getFormattedEmoji(
                        RiotHandler.getRiotApi().getDDragonAPI().getChampion(partecipant.getChampionId()).getName())
                        + " " + account.getName().toUpperCase();
                String stats = "";
                if (s.getCurrentGame().getGameQueueConfig().commonName().equals("5v5 Ranked Flex Queue")) {

                    stats = RiotHandler.getFlexStats(jda, RiotHandler.getSummonerBySummonerId(partecipant.getSummonerId(), s.getPlatform()));
                    stats = stats.substring(0, stats.lastIndexOf("P") + 1) + " | "
                            + stats.substring(stats.lastIndexOf(":") + 1);

                } else {
                    stats = RiotHandler.getSoloQStats(jda, RiotHandler.getSummonerBySummonerId(partecipant.getSummonerId(), s.getPlatform()));
                    stats = stats.substring(0, stats.lastIndexOf("P") + 1) + " | "
                            + stats.substring(stats.lastIndexOf(":") + 1);
                }
                if (partecipant.getTeam() == TeamType.BLUE)
                    blueSide += "**" + sum + "** " + stats + "\n";
                else
                    redSide += "**" + sum + "** " + stats + "\n";

            }
            if (s.getCurrentGame().getGameQueueConfig().commonName().equals("5v5 Ranked Flex Queue"))
                builder.addField("Ranked stats", "FLEX", false);

            else
                builder.addField("Ranked stats", "SOLOQ", false);

            builder.addField("**BLUE SIDE**", blueSide, false);
            builder.addField("**RED SIDE**", redSide, true);
            builder.setFooter("For every gamemode would be use the SOLOQ ranked data. Flex would be shown only if the game is a Flex game.");
            return builder;

        } catch (Exception e) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(account.getName() + "#" + account.getTag() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(RiotHandler.getSummonerProfilePic(s));
            builder.setDescription("This user is not in a game.");
            return builder;
        }
    }

}
