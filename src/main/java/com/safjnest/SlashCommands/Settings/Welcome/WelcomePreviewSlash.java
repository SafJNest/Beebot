package com.safjnest.SlashCommands.Settings.Welcome;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Bot.BotDataHandler;
import com.safjnest.Utilities.Bot.Guild.GuildData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertType;

public class WelcomePreviewSlash extends SlashCommand{

    public WelcomePreviewSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();
        String botId = event.getJDA().getSelfUser().getId();

        GuildData gs = BotDataHandler.getSettings(botId).getGuildSettings().getServer(guildId);
        
        AlertData welcome = gs.getAlert(AlertType.WELCOME);


        if(welcome == null) {
            event.deferReply(true).addContent("This guild doesn't have a welcome message.").queue();
            return;
        }

        String welcomeMessage = "The welcome message is " + (welcome.isEnabled() ? "enabled" : "disabled") + ":\n" 
        + "```" + welcome.getMessage().replace("#user", event.getUser().getAsMention()) + "```";

        welcomeMessage = welcomeMessage + "\nThis message would be sent to <#" + welcome.getChannelId() + ">";

        if(welcome.getRoles() != null){
            welcomeMessage += "\nRoles that would be given to the user:";
            for (String role : welcome.getRoles().values().toArray(new String[0])) {
                welcomeMessage += "\n" + event.getGuild().getRoleById(role).getName();
            }
        }

        event.deferReply(false).addContent(welcomeMessage).queue();
    }
    
}
