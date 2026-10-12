package com.safjnest.lol.service;
import java.util.*;

import com.R4B.api.dto.*;
import com.R4B.api.dto.Build.*;
import com.safjnest.utils.JsonCodec;

public final class BuildCodec {

    public static String toJson(Build data) {
        return JsonCodec.toJson(data);
    }

    public static Build fromJson(String json) {
        try {
            Build build = JsonCodec.fromJson(json, Build.class);
            if (build == null) return null;
            if (build.filter() != null && !(build.filter() instanceof Filter)) return null;
            if (build.coreBuilds() == null || build.coreItems() == null || build.starterOptions() == null
                    || build.bootOptions() == null || build.supportItemOptions() == null
                    || build.itemSlots() == null || build.runeOptions() == null
                    || build.summonerSpellOptions() == null || build.skillOrders() == null
                    || build.prismaticOptions() == null || build.augmentOptions() == null) return null;
            return build;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
