package com.safjnest.Commands.ManageMembers;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class Unban extends Command{

    public Unban(){
        this.name = "unban";
        this.aliases = new String[]{"unsgozz", "undestroy", "unannihilate", "unradiateDeath", "sban", "pardon"};
        this.help = "vieni perdonato da qualche dio supremo, non dimenticarti di ringraziare";
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            if(event.getArgs().length() == 0){
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Persone sgozzate");
                for (net.dv8tion.jda.api.entities.Guild.Ban ban : event.getGuild().retrieveBanList().complete())
                    eb.appendDescription(ban.getUser().getAsMention() + " - ");
                eb.setColor(new Color(0, 128, 128));
                eb.setAuthor(event.getSelfUser().getName(), "https://github.com/SafJNest",event.getSelfUser().getAvatarUrl());
                eb.setFooter("*Questo non e' rythem, questa e' perfezione cit. steve jobs (probabilmente)", null);
                event.reply(eb.build());
            }

            else{
                final User surelyTheGuy = event.getJDA().retrieveUserById(event.getArgs()).complete();
                if (!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.BAN_MEMBERS))
                    event.reply(event.getJDA().getSelfUser().getAsMention() + " non ha il permesso di sbannare");

                else if (PermissionHandler.hasPermission(event.getMember(), Permission.BAN_MEMBERS)) {
                    event.getGuild().unban(surelyTheGuy).queue(
                                                            (e) -> event.reply("Sbannato " + surelyTheGuy.getAsMention()), 
                                                            new ErrorHandler().handle(
                                                                ErrorResponse.MISSING_PERMISSIONS,
                                                                    (e) -> event.replyError("sorry, " + e.getMessage()))
                    );
                }
                else 
                    event.reply("Brutto fallito non sbannare se non sei admin UwU");
            }
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}