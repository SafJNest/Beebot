package com.safjnest.Commands.ManageMembers;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.safjnest.Utilities.JSONReader;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutroSun</a>
 * 
 * @since 1.3
 */
public class Move extends Command{

    public Move(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        User theGuy = null;
        List<Member> theGuys = null;
        VoiceChannel channel = null;
        boolean flag = true;
        String[] args = event.getArgs().split(" ");
        if(args[0].equalsIgnoreCase("me"))
            theGuy = event.getAuthor();

        else if(args[0].equalsIgnoreCase("here")){
            theGuys = event.getMember().getVoiceState().getChannel().getMembers();
            for(Member member : theGuys)
                System.out.println(member.getUser().getName());

        }else if(event.getMessage().getMentionedUsers().size() > 0){
            theGuy = event.getMessage().getMentionedUsers().get(0);
            flag = false;
        }else{
            event.reply("Non ho capito a chi vuoi spostare.");
            return;
        }
        //get the channel
        if(args[1].equalsIgnoreCase("")){
            event.reply("Non ho capito a dove vuoi spostare.");
            return;
        }else if(args[1].equalsIgnoreCase("here"))
            channel = event.getGuild().getVoiceChannelById(event.getMember().getVoiceState().getChannel().getId());
       
            else if(args[1].equalsIgnoreCase("afk")){
            channel = event.getGuild().getAfkChannel();
            if(channel == null){
                event.reply("Non hai un canale afk");
                return;
            }
        }else if(event.getMessage().getMentionedMembers().size() == 1 && flag)
            channel = event.getGuild().getVoiceChannelById(event.getMessage().getMentionedMembers().get(0).getVoiceState().getChannel().getId());
        
        else if(event.getMessage().getMentionedMembers().size() == 2)
            channel = event.getGuild().getVoiceChannelById(event.getMessage().getMentionedMembers().get(1).getVoiceState().getChannel().getId());
        
        else{
            channel = event.getGuild().getVoiceChannelById(args[1]);
            if(channel == null){
                event.reply("Non ho trovato il canale.");
                return;
            }else if(channel.getId().equals(event.getMember().getVoiceState().getChannel().getId())){
                event.reply("Sei già in quel canale odizor");
                return;
            }
        }
        if(theGuy == null){
            for(Member member : theGuys){
                event.getGuild().moveVoiceMember(member, channel).queue();
                event.reply(member.getAsMention() + " spostato in: " + channel.getName());
            }
            return;
        }else{
            event.getGuild().moveVoiceMember(event.getGuild().getMember(theGuy), channel).queue();
            event.reply(theGuy.getAsMention() + " spostato in: " + channel.getName());
        }
        event.reply("che oidozir e' successo annodam");
            

    }
}
