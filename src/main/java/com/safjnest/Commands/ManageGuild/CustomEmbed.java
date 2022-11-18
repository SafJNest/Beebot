package com.safjnest.Commands.ManageGuild;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.App;
import com.safjnest.Utilities.CommandsHandler;
import java.awt.Color;
import java.util.Collection;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

public class CustomEmbed extends Command {

    public CustomEmbed() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        //ex -title -description -color -inlineField -field -blankField - 
        //TODO classe di merda rifare tutto come zira

        Button[] buttons = {
            Button.primary("content", "Edit content"),
            Button.primary("addField", "Add field"),
            Button.primary("removeField", "Remove field"),
            Button.success("Send", "Send"),
            Button.danger("cancel", "Cancel")
        };

        MessageCreateBuilder message = new MessageCreateBuilder();
        message.addActionRow(buttons);
        

        //String args = event.getArgs();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("**Title**");
        eb.setColor(Color.decode(App.color));
        eb.setDescription("description");
        
        eb.setAuthor("author");
        eb.setFooter("Footer");
        eb.setImage("https://cdn.discordapp.com/attachments/733274067917996123/1042387797769736202/Immagine.png");
        eb.setThumbnail("https://cdn.discordapp.com/attachments/733274067917996123/1042387797769736202/Immagine.png");
        eb.setTimestamp(null);

        eb.addField("field1", "```" + "ciao" + "```", true);   
        eb.addBlankField(false);
        eb.addField("field2", "```" + "ciao2" + "```", true);

        message.setEmbeds(eb.build());

        event.reply(message.build());
    }
}
