package com.safjnest.commands.League;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Summoner extends Command {

    public Summoner() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);

        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        Button left = Button.primary("lol-left", "<-");
        Button right = Button.primary("lol-right", "->");
        Button center = Button.primary("lol-center", "f");
        Button refresh = Button.primary("lol-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        String args = event.getArgs();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        User theGuy = null;

        if (args.equals(""))
            theGuy = event.getAuthor();
        else if (event.getMessage().getMentions().getMembers().size() != 0)
            theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = RiotHandler.getSummonerByArgs(event);
        if (s == null) {
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }

        EmbedBuilder builder = createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(), s);

        RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE,
                s.getPUUID());
        center = Button.primary("lol-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();

        if (theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1) {
            event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(left, center, right, refresh).queue();
            return;
        }
        event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(center, refresh).queue();

    }

    public static EmbedBuilder createEmbed(JDA jda, String id, no.stelar7.api.r4j.pojo.lol.summoner.Summoner s){
        RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
        
        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag());
        builder.setColor(Bot.getColor());
        builder.setThumbnail(RiotHandler.getSummonerProfilePic(s));

        String userId = DatabaseHandler.getUserIdByLOLAccountId(s.getAccountId());

        if(userId != null){
            User theGuy = jda.retrieveUserById(userId).complete();
            builder.addField("User:", theGuy.getName(), true);
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), true);
            builder.addBlankField(true);

            ResultRow data = DatabaseHandler.getSummonerData(userId, s.getAccountId());
            if (data.getAsBoolean("tracking")) builder.setFooter("LPs tracking enabled for the current summoner");
            else builder.setFooter("LPs tracking disabled for the current summoner");
            
        }else{
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), false);
        }
        
        builder.addField("Solo/duo Queue", RiotHandler.getSoloQStats(jda, s), true);
        builder.addField("Flex Queue", RiotHandler.getFlexStats(jda, s), true);
        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += RiotHandler.getMastery(jda, s, i) + "\n";
        
        builder.addField("Top 3 Champs", masteryString, false); 
        builder.addField("Activity", RiotHandler.getActivity(jda, s), true);




        return builder;
    }


}