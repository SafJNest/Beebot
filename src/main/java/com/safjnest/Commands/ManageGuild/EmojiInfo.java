package com.safjnest.Commands.ManageGuild;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.DateHandler;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
//import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class EmojiInfo extends Command {

    public EmojiInfo(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        String args = event.getArgs();
        String idEmoji = null;
        CustomEmoji em = null;
        if(args.contains("<")){
            try {
                idEmoji = args.substring(args.lastIndexOf(":")+1, args.length()-1);
                em = event.getGuild().getEmojiById(idEmoji);
            } catch (Exception e) {
                event.reply("Emote non trovata");
            }
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(":laughing: "+"**EMOJI INFO**"+" :laughing:");
        eb.setThumbnail(em.getImageUrl());
        eb.addField("**Nome**", "```" + em.getName() + "```", true);   
        eb.addField("**ID emoji**", "```" + em.getId() + "```", true); 
        eb.addField("**GIF?**",
        (em.isAnimated())
            ?"```✅ Si```"
            :"```❌ No```"
        , true);
        eb.addField("**Emoji URL**", em.getImageUrl(), false);   
        eb.addField("Creata il il (dd/mm/yyyy)", "```" + DateHandler.formatDate(em.getTimeCreated()) + "```", false);
        event.reply(eb.build());
	}
}