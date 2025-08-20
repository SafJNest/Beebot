package com.safjnest.commands.misc;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Msg extends SlashCommand {

    public Msg(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "The user you want to message", true),
            new OptionData(OptionType.STRING, "msg", "The message you want to send", true),
            new OptionData(OptionType.BOOLEAN, "anonym", "True to send the message anonymously", true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        User theGuy = event.getOption("user").getAsUser();
        String author = event.getMember().getEffectiveName();
        String thumb = event.getUser().getAvatarUrl();
        String title = "NEW MESSAGE FROM " + event.getMember().getEffectiveName();
        
        String img = "punto.jpg";
        File file = new File("rsc" + File.separator + "assets" + File.separator + img);

        EmbedBuilder eb = new EmbedBuilder();
        if(event.getOption("anonym") != null && event.getOption("anonym").getAsBoolean()){
            author = event.getJDA().getSelfUser().getEffectiveName();

            thumb = "attachment://" + img;
            title = "NEW ANONYMUS MESSAGE";
            
        }

        
        eb.setTitle(title);
        
        eb.setThumbnail(thumb);
        eb.setAuthor(author);
        eb.setDescription(event.getOption("msg").getAsString());
        eb.setColor(Bot.getColor());
        
        if(event.getOption("anonym") != null && event.getOption("anonym").getAsBoolean()){
            theGuy.openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(
                eb.build())
                .addFiles(FileUpload.fromData(file))
                .queue());
                
        }else{

            theGuy.openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(
                    eb.build())
                    .queue());
        }

        event.deferReply(true).addContent("Message sent successfuly").queue();
	}
}