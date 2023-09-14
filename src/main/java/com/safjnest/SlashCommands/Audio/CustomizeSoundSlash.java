package com.safjnest.SlashCommands.Audio;

import java.util.ArrayList;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The command lets you delete a sound from the server.
 * <p>
 * You have to be a server admin to use the command.
 * </p>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class CustomizeSoundSlash extends SlashCommand {

    public CustomizeSoundSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to modify (name or id)", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "name", "New name of the sound", false),
            new OptionData(OptionType.BOOLEAN, "public", "true or false", false)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String fileName = event.getOption("sound").getAsString();
        String query = null;
        String id = null, name, userId;
        boolean wasPublic = false;
        ArrayList<ArrayList<String>> arr = null;

        if (fileName.matches("[0123456789]*"))
            query = "SELECT id, name, user_id, public FROM sound WHERE id = '" + fileName + "';";
        else
            query = "SELECT id, name, user_id, public FROM sound WHERE name = '" + fileName + "' AND (user_id = '"
                    + event.getUser().getId() + "' OR guild_id = '" + event.getGuild().getId() + "');";

        if ((arr = DatabaseHandler.getSql().getAllRows(query, 4)) == null || arr.isEmpty()) {
            event.deferReply(true).addContent("Couldn't find a sound with that name/id.").queue();
            return;
        }

        if (arr.size() > 1) {
            event.deferReply(true).addContent("You have uploaded more than one sound with that name in this server, please use IDs to choose the sound you want to modify.").queue();
            return;
        }

        id = arr.get(0).get(0);
        name = arr.get(0).get(1);
        userId = arr.get(0).get(2);
        wasPublic = arr.get(0).get(3).equals("1");

        if (!event.getUser().getId().equals(userId) && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.deferReply(false).addContent("You don't have permission to modify this sound.").queue();
            return;
        }


        if(event.getOption("name") != null)
            name = event.getOption("name").getAsString();
        if(event.getOption("public") != null)
            wasPublic = event.getOption("public").getAsBoolean();

        query = "UPDATE sound SET name = '" + name + "', public = '" + ((wasPublic ) ? "1" : "0") + "' WHERE id = '" + id + "';";

        DatabaseHandler.getSql().runQuery(query);

        String locket = (arr.get(0).get(3).equals("1")) ? ":lock:" : ":unlock:";
        String locketNew = (wasPublic == true) ? ":lock:" : ":unlock:";

        event.deferReply(false).addContent(name + " (ID: " + id + ") "+ locketNew +" has been modified in: " + name + " (ID: " + id + ") "+ locket).queue();
    }
}
