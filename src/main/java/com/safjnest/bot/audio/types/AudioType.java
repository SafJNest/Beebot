package com.safjnest.bot.audio.types;

public enum AudioType {
    AUDIO,
    SOUND,
    GREET,
    SOUNDBOARD,
    WEB;

    private static final AudioType[] VALUES = values();

    public static AudioType getFromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length)
            return null;

        return VALUES[ordinal];
    }
}
