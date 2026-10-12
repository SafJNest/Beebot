package com.safjnest.lol.service;
import java.util.*;
import org.json.*;

import com.R4B.api.dto.RuneSignature;
import com.safjnest.lol.utils.BuildUtils;
public final class RuneSignatureCodec {

    public static RuneSignature from(JSONObject runesJson) {
        JSONArray primary = runesJson.optJSONArray("primary");
        JSONArray secondary = runesJson.optJSONArray("secondary");
        JSONArray stats = runesJson.optJSONArray("stats");

        if (primary == null || primary.length() < 2 || secondary == null) return null;

        List<Integer> statList = new ArrayList<>();
        if (stats != null) {
            for (int i = 0; i < stats.length(); i++)
                statList.add(stats.optInt(i, 0));
        }

        return new RuneSignature(
                primary.optInt(0, 0),
                primary.optInt(1, 0),
                BuildUtils.extractSorted(primary, 2),
                secondary.optInt(0, 0),
                BuildUtils.extractSorted(secondary, 1),
                List.copyOf(statList)
        );
    }
}
