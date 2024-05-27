package com.safjnest.commands.Owner;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutroSun</a>
 * 
 * @since 1.3
 */
public class RandomMove extends Command{

    public RandomMove(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.ownerCommand = true;
        this.hidden = true;
    }

    @Override
    protected void execute(CommandEvent event) {
        List<Member> mentionedMembers = null;
        VoiceChannel startChannel = null;

        List<VoiceChannel> channels = event.getGuild().getVoiceChannels();

        String[] args = event.getArgs().split(" ");
        int n = Integer.parseInt(args[1]);

        if(args[0].equalsIgnoreCase("here"))
            mentionedMembers = event.getMember().getVoiceState().getChannel().getMembers();
        else
            mentionedMembers = PermissionHandler.getMentionedMembers(event, args[0]);

        startChannel = event.getGuild().getVoiceChannelById(event.getGuild().getMemberById(mentionedMembers.get(0).getId()).getVoiceState().getChannel().getId());
        
        for(int i = 0; i < n - 1; i++) {
            VoiceChannel channel = channels.get((int)(Math.random() * (channels.size()-1)));
            for(Member member : mentionedMembers)
                event.getGuild().moveVoiceMember(member, channel).queue();
        }
        for(Member member : mentionedMembers)
            event.getGuild().moveVoiceMember(member, startChannel).queue();

        event.getMessage().delete().queue();
        return;
    }
}
