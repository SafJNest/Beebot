package com.safjnest.model.guild.customcommand;

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

    public void addValue(OptionValue value) {
        values.add(value);
    }

    public String getId() {
        return String.valueOf(ID);
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public OptionType getType() {
        return type;
    }

    public List<OptionValue> getValues() {
        return values;
    }

    public int getID() {
        return ID;
    }

    public String toString() {
        return "Option{" +
                "ID=" + ID +
                ", key='" + key + '\'' +
                ", description='" + description + '\'' +
                ", isRequired=" + isRequired +
                ", type=" + type +
                ", values=" + values +
                '}';
    }





}
