package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.ProfileIndexable;
import com.safjnest.nosql.MongoDB;

public final class ProfileIndexableService {

    private ProfileIndexableService() {}

    public static List<ProfileIndexable> get() {
        return MongoDB.findProfileIndexables();
    }

    public static List<ProfileIndexable> refresh() {
        return MongoDB.refreshProfileIndexables();
    }

    // ============================================================================
}
