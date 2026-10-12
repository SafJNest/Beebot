package com.R4B.lol;

import com.R4B.api.dto.*;
public enum RecordOrder {
    HIGHEST,
    LOWEST;

    public long score(long value) {
        return this == HIGHEST ? value : -value;
    }
}
