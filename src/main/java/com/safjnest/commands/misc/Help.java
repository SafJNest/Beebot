package com.safjnest.commands.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * This commands once is called sends a message with a full list of all commands, grouped by category.
 * <p>The user can then use the command to get more information about a specific command.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.01
 */
public class Help extends SlashCommand {


    public Help() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "command", "Name of the command you want information on",
                false)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
    }


    public static EmbedBuilder getGenericHelp(String guildId, String userId) {
        String prefix = GuildCache.getGuild(guildId).getPrefix();
        EmbedBuilder eb = new EmbedBuilder();

        HashMap<String, BotCommand> commands = CommandsLoader.getCommandsData(userId);

        HashMap<String, List<BotCommand>> categories = new HashMap<>();

        for(String command : commands.keySet())
            categories.computeIfAbsent(commands.get(command).getCategory().getName(), k -> new ArrayList<>()).add(commands.get(command));

        for(String category : categories.keySet()) {
            categories.get(category).sort((c1, c2) -> {
                int c1p = (c1.isText() ? 2 : 0) + (c1.isSlash() ? 1 : 0);
                int c2p = (c2.isText() ? 2 : 0) + (c2.isSlash() ? 1 : 0);
                if(c1p == c2p)
                    return c1.getName().compareTo(c2.getName());
                return Integer.compare(c1p, c2p);
            });
        }

        eb.setTitle("📒INFO AND COMMANDS📒");
        eb.setDescription("Current prefix is: **" + prefix + "**\n"
            + "For more information on a command: **" + prefix + "help <command name>.**\n"
            + "In **brackets** is specified if the command is **text only** or **slash only**.\n"
            + "If it doesn't have brackets it's **both**. **s** means the command has **sub-commands**.");
        String ss = "```\n";

        for(String category : getCategoriesBySize(categories)) {
            for(BotCommand command : categories.get(category)) {
                String brackets = "";
                if(command.isText() != command.isSlash())
                    brackets += " [" + (command.isText() ? prefix : "") + (command.isSlash() ? "/" : "") + ((command.getChildren().size() != 0) ? "s" : "") + "]";
                else if(command.getChildren().size() != 0)
                    brackets += " [s]";
                ss += command.getName() + brackets + "\n";
            }
            ss +="```";
            eb.addField(category, ss, true);
            ss = "```\n";
        }

        int fieldNum = categories.size();
        while (fieldNum++ % 3 != 0)
            eb.addField("\u200E", "\u200E", true);
        
        eb.addField("Number of commands avaible:", "```" + commands.size() + "```", false);
        eb.setFooter("Sorry if things don't work, Beebot was made for fun by only 2 people.", null);
        eb.setColor(Bot.getColor());
        eb.setAuthor(Bot.getJDA().getSelfUser().getName(), "https://github.com/SafJNest", Bot.getJDA().getSelfUser().getAvatarUrl());


        return eb;
    }

    public static List<LayoutComponent> getChildrenButton(BotCommand command) {
        if (command.getChildren().isEmpty()) return null;
        
        List<LayoutComponent> rows = new ArrayList<>();
        List<Button> row = new ArrayList<>();

        int i = 0;
        for (BotCommand child : command.getChildren()) {
            String name = command.getName() + " " + child.getName();
            row.add(Button.primary("help-" + name, child.getName()).withEmoji(CustomEmojiHandler.getRichEmoji("command")));
            if (row.size() == 5 || i == command.getChildren().size() - 1) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
            i++;
        }
        return rows;
    }

    public static List<LayoutComponent> getChildButton(BotCommand command) {
        if (command.getChildren().isEmpty()) return null;
        
        List<LayoutComponent> rows = new ArrayList<>();

        Button back = Button.primary("help-" + command.getName(), " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        rows.add(ActionRow.of(back));

        return rows;
    }

    public static List<LayoutComponent> getCommandButton(BotCommand command) {
        if (command.getFather() == null && command.getChildren().isEmpty()) return null;
        if (command.getFather() != null) return getChildButton(command.getFather());
        
        return getChildrenButton(command);
    
    }

    public static EmbedBuilder getCommandHelp(BotCommand command) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle("**📒" + command.getName().toUpperCase() + " COMMAND📒**");
        
        eb.setDescription("```" + command.getLongHelp() + "```");
        
        eb.addField("**Category**", "```" + command.getCategory().getName() + "```", true);
        eb.addField("**Cooldown**", "```" + command.getCooldown() + "s```", true);
        
        if(command.isText()) {
            eb.addField("**Arguments** [text]", "```" + command.getArguments() + "```", false);
        }

        if(command.isSlash()) {
            if(command.getChildren().size() > 0) {
                String children = "";
                for(BotCommand child : command.getChildren()) 
                    children += child.getName() + " - ";

                children = children.substring(0, children.length() - 3);     
                eb.addField("**Children** [slash]", "```[" + children + "]```", false);
            }
            else if(command.getOptions().size() > 0) {
                String options = "";
                for(OptionData option : command.getOptions()) {
                    if(option.isRequired()) {
                        options += option.getName() + " - " + option.getDescription() + " [required]\n";
                    }
                }
                for(OptionData option : command.getOptions()) {
                    if(!option.isRequired()) {
                        options += option.getName() + " - " + option.getDescription() + "\n";
                    }
                }
                eb.addField("**Options** [slash]", "```" + options + "```", false);
            }
        }
        
        if (!command.onlySlash()) {
            String aliases = "No aliases";
            if(command.getAliases() != null && command.getAliases().length > 0) {
                aliases = "";
                for(String a : command.getAliases())
                    aliases += a + " - ";
                aliases = aliases.substring(0, aliases.length() - 3);
            }
            eb.addField("**Aliases** [text]", "```" + aliases + "```", false);
        }

        boolean twoRowPermission = command.getBotPermissions().length > 0 && command.getUserPermissions().length > 0;
        if (command.getBotPermissions().length > 0){
            String permissions = "";
            for (int i = 0; i < command.getBotPermissions().length; i++) {
                permissions += command.getBotPermissions()[i].getName() + " - ";
            }
            permissions = permissions.substring(0, permissions.length() - 3);
            eb.addField("**Bot Permissions**", "``` [" + permissions + "]```", twoRowPermission);
        }
        if (command.getUserPermissions().length > 0){
            String permissions = "";
            for (int i = 0; i < command.getUserPermissions().length; i++) {
                permissions += command.getUserPermissions()[i].getName() + " - ";
            }
            permissions = permissions.substring(0, permissions.length() - 3);
            eb.addField("**User Permissions**", "``` [" + permissions + "]```", twoRowPermission);
        }

        eb.setFooter("In arguments [] is a required field and () is an optional field", null);
        eb.setColor(Bot.getColor());
        eb.setAuthor(Bot.getJDA().getSelfUser().getName(), "https://github.com/SafJNest", Bot.getJDA().getSelfUser().getAvatarUrl());


        return eb;
    }

    public static BotCommand searchCommand(String commandName, HashMap<String, BotCommand> commands) {
        commandName = commandName.toLowerCase();

        String probablyFather = commandName.split(" ")[0];


        if(!commands.containsKey(probablyFather.toLowerCase())) {
            return null;
        }

        BotCommand commandToPrint = commands.get(probablyFather.toLowerCase());

        if (commandName.split(" ").length > 1) {
            for (BotCommand child : commandToPrint.getChildren()) {
                if (child.getName().equalsIgnoreCase(commandName.split(" ")[1].toLowerCase())) {
                    return child;
                }
            }
        }

        return commandToPrint;
    }

    @Override
    protected void execute(CommandEvent event) {
        String inputCommand = event.getArgs();
       
        EmbedBuilder eb = new EmbedBuilder();
        List<LayoutComponent> rows = null;
        if(inputCommand.equals("")) {
            eb = getGenericHelp(event.getGuild().getId(), event.getMember().getId());  
        } 
        else {
            HashMap<String, BotCommand> commands = CommandsLoader.getCommandsData(event.getMember().getId());
            BotCommand commandToPrint = searchCommand(inputCommand, commands);

            if(commandToPrint == null) {
                event.reply("Command not found");
                return;
            }
            
            eb = getCommandHelp(commandToPrint);
            rows = getCommandButton(commandToPrint);
        }

        if (rows != null) event.getChannel().sendMessageEmbeds(eb.build()).addComponents(rows).queue();
        else event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String inputCommand = (event.getOption("command") == null) ? "" : event.getOption("command").getAsString();

        EmbedBuilder eb = new EmbedBuilder();
        List<LayoutComponent> rows = null;

        if(inputCommand.equals("")) 
            eb = Help.getGenericHelp(event.getGuild().getId(), event.getMember().getUser().getId());  
        else {      
            HashMap<String, BotCommand> commands = CommandsLoader.getCommandsData(event.getMember().getId());
            BotCommand commandToPrint = Help.searchCommand(inputCommand, commands);

            eb = Help.getCommandHelp(commandToPrint);
            rows = Help.getCommandButton(commandToPrint);
        }
        
        if (rows != null) event.deferReply().addEmbeds(eb.build()).setComponents(rows).queue();
        else event.deferReply().addEmbeds(eb.build()).queue();
    }

    private static List<String> getCategoriesBySize(HashMap<String, List<BotCommand>> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort((k1, k2) -> {
            return Integer.compare(map.get(k2).size(), map.get(k1).size());
        });
        return keys;
    }
}
