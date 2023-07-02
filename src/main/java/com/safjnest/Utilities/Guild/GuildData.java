package com.safjnest.Utilities.Guild;

/**
 * Class that stores all the settings for a guild.
 * <ul>
 * <li>Prefix</li>
 * <li>ID</li>
 * </ul>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class GuildData {
    /**Server ID */
    private Long id;
    /**Prefix Server */
    private String prefix;
    /**Exp System */
    private boolean expSystem;
    /**
     * default constructor
     * @param id
     * @param prefix
     */
    public GuildData(Long id, String prefix, boolean expSystem) {
        this.id = id;
        this.prefix = prefix;
        this.expSystem = expSystem;
    }

    public Long getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean getExpSystem() {
        return expSystem;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setExpSystem(boolean expSystem) {
        this.expSystem = expSystem;
    }

    public String toString(){
        return "ID: " + id + "| Prefix: " + prefix + " |ExpSystem: " + expSystem;
    }

    
}
