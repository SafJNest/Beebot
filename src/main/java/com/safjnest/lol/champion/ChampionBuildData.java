package com.safjnest.lol.champion;

public final class ChampionBuildData {

    public record Game(BuildSignature signature, RuneSignature runes, boolean win) {}

    private ChampionBuildData() {}
}
