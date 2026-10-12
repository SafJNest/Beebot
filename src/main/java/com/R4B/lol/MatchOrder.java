package com.R4B.lol;

import com.R4B.api.dto.*;
public enum MatchOrder {

    ASC,
    DESC;

    public boolean ascending() {
        return this == ASC;
    }
}
