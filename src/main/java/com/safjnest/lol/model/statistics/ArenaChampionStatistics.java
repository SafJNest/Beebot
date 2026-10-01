package com.safjnest.lol.model.statistics;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Build;

public record ArenaChampionStatistics(int schemaVersion, int aggregationVersion, int championId, String patch,
        StatisticalLeaf overall, List<Build> builds, List<Distribution> boots, List<Distribution> items,
        List<Distribution> prismatics, List<Distribution> augments, Coverage coverage) {

    public ArenaChampionStatistics {
        builds = List.copyOf(builds);
        boots = List.copyOf(boots);
        items = List.copyOf(items);
        prismatics = List.copyOf(prismatics);
        augments = List.copyOf(augments);
    }

    public record Distribution(Build.Choice overall, List<Build.Choice> positions) {
        public Distribution { positions = List.copyOf(positions); }
    }

    public record Coverage(long matches, long participantGames, long coreGames,
            Map<String, Long> missing, Map<String, Long> ambiguous, Map<String, Long> rejected) {
        public Coverage {
            missing = Map.copyOf(missing);
            ambiguous = Map.copyOf(ambiguous);
            rejected = Map.copyOf(rejected);
        }
    }
}
