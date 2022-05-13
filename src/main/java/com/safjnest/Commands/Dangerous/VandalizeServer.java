package com.safjnest.Commands.Dangerous;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class VandalizeServer extends Command{

    public VandalizeServer(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        if(!PermissionHandler.isUntouchable(event.getAuthor().getId())){
            return;
        }

        String[] args = event.getArgs().split(" ", 3);
        Guild theGuild = event.getJDA().getGuildById(args[0]);
        Member self = theGuild.getMember(event.getSelfUser());

        

        switch (args[1]) {
            case "suffix":
                String suffix = args[2];
                theGuild.getMembers().forEach(member -> {
                    if(self.canInteract(member) && !((member.getNickname() == null) ? member.getUser().getName() : member.getNickname()).toLowerCase().endsWith(suffix.toLowerCase())){
                        member.modifyNickname(((member.getNickname() == null) ? member.getUser().getName() : member.getNickname()) + suffix).queue(
                        (e) -> System.out.println("ok"), 
                        new ErrorHandler().handle(
                            ErrorResponse.MISSING_PERMISSIONS,
                                (e) -> System.out.println("Sorry, " + e.getMessage()))
                        );
                    }
                });
                break;
            case "desuffix":
                String desuffix = args[2];
                theGuild.getMembers().forEach(member -> {
                    if(self.canInteract(member) && ((member.getNickname() == null) ? member.getUser().getName() : member.getNickname()).toLowerCase().endsWith(desuffix.toLowerCase())){
                        member.modifyNickname(((member.getNickname() == null) ? member.getUser().getName() : member.getNickname()).substring(0, ((member.getNickname() == null) ? member.getUser().getName() : member.getNickname()).length()- desuffix.length())).queue(
                        (e) -> System.out.println("ok"), 
                        new ErrorHandler().handle(
                            ErrorResponse.MISSING_PERMISSIONS,
                                (e) -> System.out.println("Sorry, " + e.getMessage()))
                        );
                    }
                });
                break;

            case "kick":
                theGuild.getMembers().forEach(member -> {
                    if(self.canInteract(member)){
                        member.kick().queue(
                        (e) -> System.out.println("ok"), 
                        new ErrorHandler().handle(
                            ErrorResponse.MISSING_PERMISSIONS,
                                (e) -> System.out.println("Sorry, " + e.getMessage()))
                        );
                    }
                });
                break;
        
            default:
                event.reply("idk");
                break;
        }
    }
}