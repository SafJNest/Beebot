package com.safjnest.core.audio.types;

public enum PlayTiming {
    NOW("Play Now"),
    NEXT("Play Next"),
    LAST("Play Last");

    private final String name;

    PlayTiming(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
