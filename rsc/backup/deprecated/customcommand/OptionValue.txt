package com.safjnest.model.guild.customcommand;

public class OptionValue {
    private final int ID;
    private String key;
    private String value;

    public OptionValue(int ID, String key, String value) {
        this.ID = ID;
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getID() {
        return ID;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "OptionValue{" +
                "ID=" + ID +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
    

}
