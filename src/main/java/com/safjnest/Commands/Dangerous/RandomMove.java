package com.safjnest.Commands.Dangerous;

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
public class RandomMove extends Command{

    public RandomMove(){
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
        List<VoiceChannel> channels = null;
        VoiceChannel channel = null;
        
        int n;
        String[] args = event.getArgs().split(" ");
        if(args[0].equalsIgnoreCase("me"))
            theGuy = event.getAuthor();

        if(args[0].equalsIgnoreCase("here")){
            theGuys = event.getMember().getVoiceState().getChannel().getMembers();

        }else if(event.getMessage().getMentionedUsers().size() > 0){
            theGuy = event.getMessage().getMentionedUsers().get(0);
        }else{
            event.reply("Non ho capito a chi vuoi rompere il cazzo.");
            return;
        }
        n = Integer.parseInt(args[1]);
        channels = event.getGuild().getVoiceChannels();
        if(theGuy == null){
            for(int i = 0; i < n; i++){
                channel = channels.get((int)(Math.random() * (channels.size()-1)));
                for(Member member : theGuys){
                    event.getGuild().moveVoiceMember(member, channel).queue();
                }
            }
            return;
        }
        for(int i = 0; i < n; i++){
            channel = channels.get((int)(Math.random() * (channels.size()-1)));
            event.getGuild().moveVoiceMember(event.getGuild().getMember(theGuy), channel).queue();
        }
        return;
            

    }
}
