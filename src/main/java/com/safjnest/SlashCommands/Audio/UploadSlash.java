package com.safjnest.SlashCommands.Audio;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class UploadSlash extends SlashCommand{
    private String fileName;
    private SQL sql;
    
    public UploadSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Sound name", true),
            new OptionData(OptionType.ATTACHMENT, "file", "Sound file (mp3 or opus)", true),
            new OptionData(OptionType.BOOLEAN, "public", "true or false", false)
        );
        this.sql = DatabaseHandler.getSql();
    }
    
	@Override
	protected void execute(SlashCommandEvent event) {
        fileName = event.getOption("name").getAsString();
        Attachment attachment = event.getOption("file").getAsAttachment();
        boolean isPublic = true;
        if(event.getOption("public") != null){
            isPublic = event.getOption("public").getAsBoolean();
        }
        
        if(fileName.matches("[0123456789]*")){
            event.reply("You can't use a name that only contains numbers.");
            return;
        }

        if(!attachment.getFileExtension().equals("mp3") && !attachment.getFileExtension().equals("opus")){
            event.deferReply(true).addContent("Only upload the sound in **mp3** or **opus** format.").queue();
            return;
        }
        
        String query = "INSERT INTO sound(name, guild_id, user_id, extension, public) VALUES('" 
                     + fileName + "','" + event.getGuild().getId() + "','" + event.getMember().getId() + "','" + attachment.getFileExtension() + "', '" + ((isPublic == true) ? "1" : "0") + "');";
        sql.runQuery(query);           
        query = "SELECT id FROM sound WHERE name = '" + fileName + "' AND guild_id = '" + event.getGuild().getId() + "' AND user_id = '" + event.getMember().getId() + "' ORDER BY id DESC LIMIT 1;";
        String id = sql.getString(query, "id");

        if(id.equals(null)){
            event.deferReply(true).addContent("An error with the database occured.").queue();
            return;
        }

        File saveFile = new File("rsc" + File.separator + "SoundBoard" + File.separator + (id + "." + attachment.getFileExtension()));

        attachment.getProxy().downloadToFile(saveFile);
        event.deferReply(false).addContent("File uploaded succesfully").queue();
	}
}
