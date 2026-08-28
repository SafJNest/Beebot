package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileMatchupLeaf extends Stats<Void> {
    public Map<Integer, Stats<Void>> matchups = new LinkedHashMap<>();
}
