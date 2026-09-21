package com.safjnest.lol.model.record;

public enum RecordOrder {
    HIGHEST,
    LOWEST;

    public long score(long value) {
        return this == HIGHEST ? value : -value;
    }
}
