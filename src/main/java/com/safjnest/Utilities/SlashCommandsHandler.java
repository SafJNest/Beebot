package com.safjnest.Utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.safjnest.SlashCommands.Audio.ConnectSlash;
import com.safjnest.SlashCommands.Audio.DisconnectSlash;
import com.safjnest.SlashCommands.Audio.ListSlash;
import com.safjnest.SlashCommands.Audio.ListUserSlash;
import com.safjnest.SlashCommands.Audio.PlaySoundSlash;
import com.safjnest.SlashCommands.LOL.SummonerSlash;
import com.safjnest.SlashCommands.ManageGuild.ChannelInfoSlash;
import com.safjnest.SlashCommands.ManageGuild.LeaderboardSlash;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
//TODO fare sta classe di merda carina, fixare tjelistner, fixare in bot, provare a fare tutto in maniera intelligente
//magare fare la dichiarazione dei comandi direttamente in app
//come cazzo gli passo ai comandi gli oggetti da qui se ho le robe in bot.java?
//dai panslung aiutami a trovare un modo figo per dichiaare tutti gli slash command da qui
public class SlashCommandsHandler {

    public static Map<String, SlashCommand> slashCommands = new HashMap<>();
    public SlashCommandsHandler() {
        System.out.println("SlashCommandsHandler");
            slashCommands.put(new ConnectSlash().getName(), new ConnectSlash());

            slashCommands.put(new ConnectSlash().getName(), new ConnectSlash());
            slashCommands.put(new DisconnectSlash().getName(), new DisconnectSlash());
            slashCommands.put(new ListSlash().getName(), new ListSlash());
            slashCommands.put(new ListUserSlash().getName(), new ListUserSlash());
            slashCommands.put(new PlaySoundSlash().getName(), new PlaySoundSlash());
                slashCommands.put(new ChannelInfoSlash().getName(), new ChannelInfoSlash());
                slashCommands.put(new LeaderboardSlash().getName(), new LeaderboardSlash());
                slashCommands.put(new SummonerSlash().getName(), new SummonerSlash());

    }

    public static SlashCommand getCommand(String name){
        return slashCommands.get(name);
    }

    public static  Collection<CommandData> getCommandData(){
        slashCommands.keySet().forEach(s -> System.out.println(s));
        Collection<CommandData> commandDataList = new ArrayList<>();
        for (SlashCommand command : slashCommands.values()) {
            commandDataList.add(command.buildCommandData());
        }
        return commandDataList;
    }
}
