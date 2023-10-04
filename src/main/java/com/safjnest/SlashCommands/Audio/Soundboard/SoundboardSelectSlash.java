package com.safjnest.SlashCommands.Audio.Soundboard;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.Color;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;


/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 2.1
 */
public class SoundboardSelectSlash extends SlashCommand{

    public SoundboardSelectSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Soundboard to play", true)
                .setAutoComplete(true));
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        
        String soundboard = event.getOption("name").getAsString();
        String query = "select soundboard_sounds.sound_id, sound.extension, sound.name from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + soundboard + "' AND  soundboard.guild_id = '" + event.getGuild().getId() + "'";
        ArrayList<ArrayList<String>> sounds = DatabaseHandler.getSql().getAllRows(query, 3);
        query = "select name from soundboard where id = '" + soundboard + "' AND guild_id = '" + event.getGuild().getId() + "'";
        String name = DatabaseHandler.getSql().getString(query, "name");
        EmbedBuilder eb = new  EmbedBuilder();
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setTitle("Soundboard: " + name);
        eb.setDescription("Press a button to play a sound");
        eb.setColor(Color.decode(
            BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
        List<Button> buttonsRowOne = new ArrayList<>();
        List<Button> buttonsRowTwo = new ArrayList<>();
        int cont = 0;
        for(ArrayList<String> s : sounds){
            if(cont < 4)
                buttonsRowOne.add(Button.primary("soundboard-" + s.get(0) + "." + s.get(1), s.get(2)));
            else
                buttonsRowTwo.add(Button.primary("soundboard-" + s.get(0)+ "." + s.get(1), s.get(2)));
            cont++;
        }
        
        if(buttonsRowTwo.size() == 0){
            event.deferReply(false).addEmbeds(eb.build()).setComponents(ActionRow.of(buttonsRowOne)).queue();
            return;
        }
        event.deferReply(false).addEmbeds(eb.build()).setComponents(ActionRow.of(buttonsRowOne), ActionRow.of(buttonsRowTwo)).queue();
    }
}
