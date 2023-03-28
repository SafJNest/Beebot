package com.safjnest.Utilities.Listeners;
import com.safjnest.Utilities.EXPSystem.ExpSystem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * This class handles all events that could occur during the listening:
 * <ul>
 * <li>On update of a voice channel (to make the bot leave an empty voice channel)</li>
 * <li>On join of a user (to make the bot welcome the new member)</li>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class TheListenerBeebot extends ListenerAdapter{
    private ExpSystem farm;

    public TheListenerBeebot(){
        farm = new ExpSystem();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        if(e.getAuthor().isBot())
            return;
            
        int lvl = farm.receiveMessage(e.getAuthor().getId(), e.getGuild().getId());
        if(lvl != -1)
            e.getChannel().asTextChannel().sendMessage("Congratulations " + e.getAuthor().getAsMention() + ", you have just leveled up to lvl: " + lvl).queue();

        
    }


}