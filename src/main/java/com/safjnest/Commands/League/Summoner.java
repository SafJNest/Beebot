package com.safjnest.Commands.League;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;

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

    public Summoner(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        Button left = Button.primary("lol-left", "<-");
        Button right = Button.primary("lol-right", "->");
        Button center = Button.primary("lol-center", "f");

        String args = event.getArgs();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        User theGuy = null;


        if(args.equals("")) theGuy = event.getAuthor();    
        else if(event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);
        
        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }

        EmbedBuilder builder = createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(), s);        
        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("lol-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();
            event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(left, center, right).queue();
            return;
        }

        event.reply(builder.build());
	}

    public static EmbedBuilder createEmbed(JDA jda, String id, no.stelar7.api.r4j.pojo.lol.summoner.Summoner s){
        RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
        
        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag());
        builder.setColor(Bot.getColor());
        builder.setThumbnail(RiotHandler.getSummonerProfilePic(s));

        String userId = DatabaseHandler.getUserIdByLOLAccountId(s.getAccountId());
        System.out.println(userId);
        if(userId != null){
            User theGuy = jda.retrieveUserById(userId).complete();
            builder.addField("User:", theGuy.getName(), true);
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), true);
            builder.addBlankField(true);
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