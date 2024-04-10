package com.safjnest.Utilities.Guild.CustomCommand;

import java.util.List;

import net.dv8tion.jda.api.interactions.commands.OptionType;


public class Option {
    private final int ID;
    private String key;
    private String description;
    private boolean isRequired;
    private OptionType type;
    private List<OptionValue> values;

    public Option(int ID, String key, String description, boolean isRequired, OptionType type) {
        this.ID = ID;
        this.key = key;
        this.description = description;
        this.isRequired = isRequired;
        this.type = type;
        this.values = null;
    }

    public Option(int ID, String key, String description, boolean isRequired, List<OptionValue> values) {
        this.ID = ID;
        this.key = key;
        this.description = description;
        this.isRequired = isRequired;
        this.type = OptionType.STRING;
        this.values = values;
    }





}
