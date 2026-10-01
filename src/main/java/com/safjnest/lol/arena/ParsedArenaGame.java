package com.safjnest.lol.arena;

import java.util.List;
import java.util.Map;

public record ParsedArenaGame(
        Integer subTeamPlacement,
        boolean win,
        Core core,
        List<Item> items,
        List<Augment> augments,
        List<Prismatic> prismatics,
        Coverage coverage) {

    public ParsedArenaGame {
        items = List.copyOf(items);
        augments = List.copyOf(augments);
        prismatics = List.copyOf(prismatics);
    }

    public record Core(
            int firstPrismaticId,
            int bootsId,
            List<Integer> snapshotItemIds,
            long firstPrismaticTimestampMillis,
            long snapshotTimestampMillis) {
        public Core {
            snapshotItemIds = List.copyOf(snapshotItemIds);
        }

        public CoreIdentity identity() {
            return new CoreIdentity(firstPrismaticId, bootsId, snapshotItemIds);
        }
    }

    public record CoreIdentity(int firstPrismaticId, int bootsId, List<Integer> snapshotItemIds) {
        public CoreIdentity {
            snapshotItemIds = List.copyOf(snapshotItemIds);
        }
    }

    public record Item(int id, int position, long timestampMillis) {}

    public record Augment(int id, int position, Long timestampMillis) {}

    public record Prismatic(int id, Long timestampMillis) {}

    public record Coverage(int missing, int ambiguous, int rejected,
            Map<String, Integer> missingReasons,
            Map<String, Integer> ambiguousReasons,
            Map<String, Integer> rejectedReasons) {
        public Coverage {
            missingReasons = Map.copyOf(missingReasons);
            ambiguousReasons = Map.copyOf(ambiguousReasons);
            rejectedReasons = Map.copyOf(rejectedReasons);
        }
    }
}
