package com.safjnest.util.spotify.type;

import net.dv8tion.jda.api.components.selections.SelectOption;

public enum SpotifyTimeRange {
    SHORT_TERM("short_term", "Last 4 weeks"),
    MEDIUM_TERM("medium_term", "Last 6 months"),
    LONG_TERM("long_term", "Last 12 months"),
    FULL_TERM("full_term", "All time");

    private final String apiLabel;
    private final String displayLabel;

    SpotifyTimeRange(String apiLabel, String displayLabel) {
        this.apiLabel = apiLabel;
        this.displayLabel = displayLabel;
    }

    public static SpotifyTimeRange fromApiLabel(String label) {
        for (SpotifyTimeRange range : values()) {
            if (range.getLabel().equals(label)) {
                return range;
            }
        }
        return SHORT_TERM;
    }

    public String getLabel() {
        return this.apiLabel;
    }

    public String getDisplayLabel() {
        return this.displayLabel;
    }

    public String toButtonId() {
        return "spotify-time-range-" + this.name().toLowerCase();
    }

    public SelectOption toSelectOption() {
        return SelectOption.of(this.getDisplayLabel(), this.getLabel());
    }
    
}
