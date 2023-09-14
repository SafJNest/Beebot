package com.safjnest.SlashCommands.Audio.Soundboard;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.Color;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;


/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 3.0
 */
public class SoundboardCreateSlash extends SlashCommand{

    public SoundboardCreateSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Without a name the soundboard would not be saved.", false),
            new OptionData(OptionType.STRING, "sound-1", "Sound 1", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-2", "Sound 2", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-3", "Sound 3", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-4", "Sound 4", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-5", "Sound 5", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-6", "Sound 6", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-7", "Sound 7", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-8", "Sound 8", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-9", "Sound 9", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound-10", "Sound 10", false).setAutoComplete(true));
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        
        String sound = "sound-";
        String sqlIn = "";
        String query = "";
        String name = "temporary";
        ArrayList<String> sounds = new ArrayList<String>();
        for(int i = 1; i <= 10; i++){
            if(event.getOption(sound + i) != null){
                sounds.add(event.getOption(sound + i).getAsString());
                sqlIn += "'" + event.getOption(sound + i).getAsString() + "', ";
            }
        }
        if(event.getOption("name") != null){
            query = "INSERT INTO soundboard (name, guild_id) VALUES ('" + event.getOption("name").getAsString() + "', '" + event.getGuild().getId() + "')";
            DatabaseHandler.getSql().runQuery(query);
            query = "SELECT id FROM soundboard WHERE name = '" + event.getOption("name").getAsString() + "' AND guild_id = '" + event.getGuild().getId() + "'";
            String id = DatabaseHandler.getSql().getString(query, "id");
            for(String s : sounds){
                query = "INSERT INTO soundboard_sounds (id, sound_id) VALUES ('" + id + "', '" + s + "')";
                DatabaseHandler.getSql().runQuery(query);
            }
            name = event.getOption("name").getAsString();
        }

        query = "SELECT id, name, extension FROM sound WHERE id IN (" + sqlIn + "'')";
        ArrayList<ArrayList<String>> soundsArr = DatabaseHandler.getSql().getAllRows(query, 3);
        EmbedBuilder eb = new  EmbedBuilder();
        eb.setAuthor(event.getMember().getUser().getName());
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setTitle("Soundboard: " + name);
        eb.setDescription("Press a button to play a sound");
        eb.setColor(Color.decode(
            BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
        List<Button> buttonsRowOne = new ArrayList<>();
        List<Button> buttonsRowTwo = new ArrayList<>();
        int cont = 0;
        for(ArrayList<String> s : soundsArr){
            if(cont < 4)
                buttonsRowOne.add(Button.primary("soundboard-" + s.get(0) + "." + s.get(2), s.get(1)));
            else
                buttonsRowTwo.add(Button.primary("soundboard-" + s.get(0)+ "." + s.get(2), s.get(1)));
            cont++;
        }
        if(buttonsRowTwo.size() == 0){
            event.deferReply(false).addEmbeds(eb.build()).setComponents(ActionRow.of(buttonsRowOne)).queue();
            return;
        }
        event.deferReply(false).addEmbeds(eb.build()).setComponents(ActionRow.of(buttonsRowOne), ActionRow.of(buttonsRowTwo)).queue();

    }    
}
