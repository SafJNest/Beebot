package com.safjnest.commands.Audio.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.SoundBoard;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The command lets you modify one of your sounds.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class CustomizeSoundSlash extends SlashCommand {

    public CustomizeSoundSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "user_sound", "Sound to modify (name or id)", true).setAutoComplete(true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String fileName = event.getOption("user_sound").getAsString();

        ResultRow toPlay = fileName.matches("[0123456789]*") 
                           ? DatabaseHandler.getAuthorSoundById(fileName, event.getMember().getId()) 
                           : DatabaseHandler.getAuthorSoundByName(fileName, event.getMember().getId());

        if(toPlay == null) {
            event.reply("Couldn't find a sound with that name/id (you can only change one of your sounds).");
            return;
        }

        ResultRow plays = DatabaseHandler.getPlays(toPlay.get("id"), event.getUser().getId());
        EmbedBuilder eb = getEmbed(event.getUser(), toPlay, plays);

        event.replyEmbeds(eb.build()).setComponents(SoundBoard.getSoundButton(toPlay.get("id"))).queue();
        
    }

    public static EmbedBuilder getEmbed(User user, String sound) {
        ResultRow toPlay = DatabaseHandler.getSoundById(sound);
        ResultRow plays = DatabaseHandler.getPlays(sound, user.getId());
        return getEmbed(user, toPlay, plays);
    }


    public static EmbedBuilder getEmbed(User user, ResultRow toPlay, ResultRow plays) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(user.getName(), "https://github.com/SafJNest", user.getAvatarUrl());
        eb.setTitle("Customize sound");
        eb.setDescription("```" + toPlay.get("name") + " (ID: " + toPlay.get("id") + ") "  + "```");
        eb.setColor(Bot.getColor());
        eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.addField("Creation time", 
            "<t:" + toPlay.getAsEpochSecond("time") + ":f>"  + " | <t:" + toPlay.getAsEpochSecond("time") + ":R>",
        false);

        return eb;
    }
}
