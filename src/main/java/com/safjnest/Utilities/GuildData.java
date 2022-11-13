package com.safjnest.Utilities;


public class GuildData {
    /**Server ID */
    private Long id;
    /**Prefix Server */
    private String prefix;

    public GuildData(Long id, String prefix) {
        this.id = id;
        this.prefix = prefix;
    }

    public Long getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }
}
