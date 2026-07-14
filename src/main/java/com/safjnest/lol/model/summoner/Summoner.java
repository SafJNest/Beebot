package com.safjnest.lol.model.summoner;

public record Summoner(
    int summonerId,
    String puuid,
    String riotId,
    String region,
    int level,
    int icon
) {
    public String name() {
        return riotIdPart(0);
    }

    public String tag() {
        return riotIdPart(1);
    }

    private String riotIdPart(int index) {
        if (riotId == null || riotId.isBlank()) return "";
        String[] parts = riotId.split("#", 2);
        return index < parts.length ? parts[index] : "";
    }
}
