package com.safjnest.sound.model;

public record SoundTarget(String id, boolean soundboard) {
    public static SoundTarget sound(String id) {
        return new SoundTarget(id, false);
    }

    public static SoundTarget soundboard(String id) {
        return new SoundTarget(id, true);
    }
}
