package com.safjnest.lol.model.match;

public enum RankHistoryView {
    PROFILE;

    public String value() {
        return name().toLowerCase();
    }
}
