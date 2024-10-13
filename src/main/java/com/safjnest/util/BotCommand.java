package com.safjnest.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.Command.Category;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BotCommand {
    private String name;
    private Category category;
    private String help;
    private String longHelp;
    private String[] aliases;
    private int cooldown;
    private String arguments;
    private boolean text;
    private boolean slash;
    private BotCommand father;
    private List<BotCommand> children = new ArrayList<BotCommand>();
    private List<OptionData> options = new ArrayList<OptionData>();
    private Permission[] botPermissions;
    private Permission[] userPermissions;
    private boolean hidden;

    public BotCommand(String name, String category, String help, String longHelp, String arguments, String[] aliases, int cooldown) {
        this.name = name;
        this.category = new Category(category);
        this.help = help;
        this.longHelp = longHelp;
        this.arguments = arguments;
        this.aliases = aliases;
        this.cooldown = cooldown;
        this.text = false;
        this.slash = false;
        this.hidden = false;
    }

    public BotCommand(String name, String category, String help, String longHelp, String arguments, String[] aliases, int cooldown, BotCommand father) {
        this.name = name;
        this.category = new Category(category);
        this.help = help;
        this.longHelp = longHelp;
        this.arguments = arguments;
        this.aliases = aliases;
        this.cooldown = cooldown;
        this.father = father;
        this.text = false;
        this.slash = false;
        this.hidden = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = new Category(category);
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getLongHelp() {
        return longHelp;
    }

    public String[] getAliases() {
        return aliases;
    }

    public void setAliases(String[] aliases) {
        this.aliases = aliases;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }




    public List<BotCommand> getChildren() {
        return children;
    }

    public BotCommand getChild(String name) {
        for (BotCommand child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return new BotCommand(name, "unknown", "unknown", "unknown", "unknown", null, 0);
    }

    public void addChild(BotCommand child) {
        this.children.add(child);
    }

    public BotCommand getFather() {
        return father;
    }



    public List<OptionData> getOptions() {
        return options;
    }

    public void setOptions(List<OptionData> options) {
        this.options = options;
    }



    public Permission[] getBotPermissions() {
        return botPermissions;
    }

    public Permission[] getUserPermissions() {
        return userPermissions;
    }



    public boolean isHidden() {
        return hidden;
    }


    public boolean isText() {
        return text;
    }

    public void setText(boolean text) {
        this.text = text;
    }

    public boolean isSlash() {
        return slash;
    }

    public void setSlash(boolean slash) {
        this.slash = slash;
    }

    public boolean isPresent() {
        return isText() || isSlash();
    }

    public boolean onlySlash() {
        return isSlash() && !isText();
    }


    public void setThings(SlashCommand command) {
        Class<?> clazz = command.getClass();

        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("execute")) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                for(int i = 0; i < parameterTypes.length; i++) {
                    if(parameterTypes[i].getName().equals("com.jagrosh.jdautilities.command.SlashCommandEvent")) {
                        this.slash = true;
                        this.options = command.getOptions();
                    } else if(parameterTypes[i].getName().equals("com.jagrosh.jdautilities.command.CommandEvent")) {
                        this.text = true;
                    }
                }
            }
        }

        this.botPermissions = command.getBotPermissions();
        this.userPermissions = command.getUserPermissions();
    }
    
    public void setThings(Command command) {
        this.text = true;
        this.botPermissions = command.getBotPermissions();
        this.userPermissions = command.getUserPermissions();
    }

}