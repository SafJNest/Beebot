package com.safjnest.lol.model.match;

public enum MatchOrder {

    ASC,
    DESC;

    public boolean ascending() {
        return this == ASC;
    }
}
