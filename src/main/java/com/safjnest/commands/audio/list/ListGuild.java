package com.safjnest.commands.audio.list;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class ListGuild extends SlashCommand{

    public ListGuild(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        Button left = Button.danger("list-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        left = left.asDisabled();
        Button right = Button.primary("list-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button center = Button.primary("list-center", "Page: 1");
        center = center.withStyle(ButtonStyle.SUCCESS);
        center = center.asDisabled();

        Button order = Button.secondary("list-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));

        EmbedBuilder eb = new  EmbedBuilder();
        eb.setAuthor(event.getUser().getName(), "https://github.com/SafJNest", event.getUser().getAvatarUrl());
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setTitle("List of " + event.getGuild().getName());
        eb.setColor(Bot.getColor());

        QueryCollection sounds = BotDB.getlistGuildSounds(event.getGuild().getId());

        eb.setDescription("Total Sound: " + sounds.size());
        
        for(int i = 0; i < sounds.size() && i < 24; i++){
            QueryRecord sound = sounds.get(i);
            String locket = sound.getAsBoolean("public") ? "" : ":lock:";
            eb.addField("**" + sound.get("name") + "**" + locket, "ID: " + sound.get("id"), true);
        }
         
        if(sounds.size() <= 24){
            right = right.withStyle(ButtonStyle.DANGER);
            right = right.asDisabled();
        }
         
        event.deferReply(false).addEmbeds(eb.build()).addComponents(ActionRow.of(left, center, right, order)).queue();
    }

}