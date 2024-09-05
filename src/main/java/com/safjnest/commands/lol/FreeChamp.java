package com.safjnest.commands.lol;

import java.io.File;


import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.lol.builders.champion.ChampionBuilder;
import no.stelar7.api.r4j.pojo.lol.champion.ChampionRotationInfo;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class FreeChamp extends SlashCommand {
    
    /**
     * Constructor
     */
    public FreeChamp(){
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
        ChampionBuilder builder = new ChampionBuilder().withPlatform(LeagueShard.EUW1);
        ChampionRotationInfo c = builder.getFreeToPlayRotation();
            
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getAuthor().getName());
        eb.setColor(Bot.getColor());
        eb.setTitle("Current free champion rotation:");

        String s = "";
        int cont = 1;
        for(StaticChampion ce : c.getFreeChampions()){
            s += CustomEmojiHandler.getFormattedEmoji(ce.getName()) + " **" + ce.getName()+"**\n";
            if(cont % 10 == 0){
                eb.addField("", s, true);
                cont = 0;
                s = "";
            }
            cont++;
        }
        String img = "iconLol.png";
        File file = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        event.getChannel().sendMessageEmbeds(eb.build())
            .addFiles(FileUpload.fromData(file))
            .queue();
	}

    @Override
	protected void execute(SlashCommandEvent event) {
        ChampionBuilder builder = new ChampionBuilder().withPlatform(LeagueShard.EUW1);
        ChampionRotationInfo c = builder.getFreeToPlayRotation();
            
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getMember().getEffectiveName());
        eb.setColor(Bot.getColor());
        eb.setTitle("Current free champion rotation:");

        String s = "";
        int cont = 1;
        for(StaticChampion ce : c.getFreeChampions()){
            s += CustomEmojiHandler.getFormattedEmoji(ce.getName()) + " **" + ce.getName()+"**\n";
            if(cont % 10 == 0){
                eb.addField("", s, true);
                cont = 0;
                s = "";
            }
            cont++;
        }
        
        String img = "iconLol.png";
        File file = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setThumbnail("attachment://" + img);
        event.deferReply(false).addEmbeds(eb.build()).addFiles(FileUpload.fromData(file)).queue();
	}

}
