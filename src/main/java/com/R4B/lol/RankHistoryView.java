package com.R4B.lol;

import com.R4B.api.dto.*;
public enum RankHistoryView {
    PROFILE;

    public String value() {
        return name().toLowerCase();
    }
}
