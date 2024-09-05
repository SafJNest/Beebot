package com.safjnest.commands.members.move;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutroSun</a>
 * 
 * @since 1.3
 */
public class MoveChannel extends SlashCommand{

    public MoveChannel(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "room", "Room to move", true)
                .setChannelTypes(ChannelType.VOICE),
            new OptionData(OptionType.CHANNEL, "destroom", "Destination Room", false)
                .setChannelTypes(ChannelType.VOICE),
            new OptionData(OptionType.USER, "destuser", "Destination user", false),
            new OptionData(OptionType.BOOLEAN, "swap", "Swap the users in the rooms you are moving", false));

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {

        List<Member> theGuys = null;

        VoiceChannel channel = null;
        
        if(event.getOption("destroom") != null){
            if(event.getOption("destroom").getChannelType() != ChannelType.VOICE){
                event.deferReply(true).addContent("Select a voice channel.").queue();
                return;
            }
            channel = event.getOption("destroom").getAsChannel().asVoiceChannel();
        }else{
            Member destGuy = event.getOption("destuser").getAsMember();
            if(destGuy.getVoiceState().getChannel() == null){
                event.deferReply(true).addContent(destGuy.getEffectiveName()+ " needs to be in a voice channel.").queue();
                return;
            }
            channel = destGuy.getVoiceState().getChannel().asVoiceChannel();
        }

        boolean swap = event.getOption("swap") != null && event.getOption("swap").getAsBoolean();
        
        VoiceChannel oldChannel = null;
        List<Member> destGuys = null;
        if (swap) {
            oldChannel = event.getMember().getVoiceState().getChannel().asVoiceChannel();
            destGuys = channel.getMembers();
        }
        
        
        theGuys = event.getGuild().getVoiceChannelById(event.getOption("room").getAsChannel().getId()).getMembers();
        for(Member member : theGuys){
            event.getGuild().moveVoiceMember(member, channel).queue();
        }

        if (swap) {
            for(Member member : destGuys){
                event.getGuild().moveVoiceMember(member, oldChannel).queue();
            }
        }

        
        event.deferReply(false).addContent("Moved  "+theGuys.size() +" users in:        "+ channel.getName() + ".").queue();

    }
}
