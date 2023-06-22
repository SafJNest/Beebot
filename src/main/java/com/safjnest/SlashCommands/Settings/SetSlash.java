package com.safjnest.SlashCommands.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.SlashCommands.Audio.ConnectSlash;
import com.safjnest.SlashCommands.Audio.DeleteSoundSlash;
import com.safjnest.SlashCommands.Audio.DisconnectSlash;
import com.safjnest.SlashCommands.Audio.DownloadSoundSlash;
import com.safjnest.SlashCommands.Audio.ListSlash;
import com.safjnest.SlashCommands.Audio.ListUserSlash;
import com.safjnest.SlashCommands.Audio.PlaySoundSlash;
import com.safjnest.SlashCommands.Audio.PlayYoutubeSlash;
import com.safjnest.SlashCommands.Audio.StopSlash;
import com.safjnest.SlashCommands.Audio.TTSSlash;
import com.safjnest.SlashCommands.Audio.UploadSlash;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Commands.CommandsLoader;
import com.safjnest.Utilities.Guild.GuildSettings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SetSlash extends SlashCommand{
    private SQL sql;
    GuildSettings gs;
    public SetSlash(SQL sql, GuildSettings gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new ConnectSlash(), new DeleteSoundSlash(), new DisconnectSlash(), new DownloadSoundSlash(), new ListSlash(), new ListUserSlash(),new PlaySoundSlash(), 
        new UploadSlash(), new StopSlash(), new SetVoiceSlash());
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);                                 
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }
}
