package com.safjnest.springapi.api.model;

import java.time.LocalDate;

public class Sound {

    private int id;
    private String name;
    private String guildId;
    private String userId;
    private String extension;
    private boolean isPublic;
    private LocalDate createdAt;

    public Sound(int id, String name, String guildId, String userId, String extension, boolean isPublic, LocalDate createdAt) {
        this.id = id;
        this.name = name;
        this.guildId = guildId;
        this.userId = userId;
        this.extension = extension;
        this.isPublic = isPublic;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

}
