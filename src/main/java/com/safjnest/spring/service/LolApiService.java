package com.safjnest.spring.service;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.lol.model.ProfileChampionStats;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.lol.model.SummonerProfile;
import com.safjnest.lol.model.SummonerRank;
import com.safjnest.lol.model.SummonerSearchResult;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.service.ProfilePageService;
import com.safjnest.spring.dto.LolProfileView;
import com.safjnest.spring.dto.LolSearchResult;
import com.safjnest.spring.util.LolApiMapper;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class LolApiService {

    private final ProfilePageService profilePageService;

    public LolApiService() {
        this.profilePageService = new ProfilePageService();
    }

    public List<LolSearchResult> search(LeagueShard shard, String query) {
        List<LolSearchResult> response = new ArrayList<>();
        for (SummonerSearchResult result : LeagueService.searchSummoners(query, shard)) {
            RiotId riotId = RiotId.parse(result.riotId());
            int totalGames = result.wins() + result.losses();
            response.add(new LolSearchResult(
                result.puuid(),
                result.riotId(),
                riotId.name(),
                riotId.tag(),
                result.region(),
                result.rank(),
                result.lp(),
                result.wins(),
                result.losses(),
                LolApiMapper.rounded(LolApiMapper.ratio(result.wins(), totalGames) * 100)
            ));
        }
        return response;
    }

    public LolProfileView profile(LeagueShard shard, String puuid) {
        ProfilePageData data = profilePageService.getProfile(puuid, shard);
        return data != null ? toProfileView(data) : null;
    }

    public LolProfileView profileByName(LeagueShard shard, String gameName, String tagLine) {
        String puuid = LeagueService.getPuuidByRiotId(gameName, tagLine, shard);
        return puuid != null ? profile(shard, puuid) : null;
    }

    private LolProfileView toProfileView(ProfilePageData data) {
        return new LolProfileView(
            toProfile(data.profile(), data.rank()),
            toSummary(data.summary()),
            toRoles(data.roles()),
            toTopChampions(data.topChampions()),
            toRecentMatches(data.recentMatches())
        );
    }

    private LolProfileView.Profile toProfile(SummonerProfile profile, SummonerRank rank) {
        RiotId riotId = RiotId.parse(profile.riotId());
        int totalGames = rank.wins() + rank.losses();
        return new LolProfileView.Profile(
            profile.puuid(),
            profile.riotId(),
            riotId.name(),
            riotId.tag(),
            profile.region(),
            profile.level(),
            profile.icon(),
            profileIcon(profile.icon()),
            rank.rank(),
            rank.lp(),
            rank.wins(),
            rank.losses(),
            LolApiMapper.rounded(LolApiMapper.ratio(rank.wins(), totalGames) * 100)
        );
    }

    private LolProfileView.Summary toSummary(ProfilePageData.Summary summary) {
        return new LolProfileView.Summary(
            summary.form(),
            summary.mainRole(),
            summary.avgKda(),
            summary.avgDamage()
        );
    }

    private List<LolProfileView.RoleStat> toRoles(List<ProfilePageData.RoleStat> roles) {
        List<LolProfileView.RoleStat> response = new ArrayList<>();
        for (ProfilePageData.RoleStat role : roles) {
            response.add(new LolProfileView.RoleStat(role.role(), role.games(), role.rate()));
        }
        return response;
    }

    private List<LolProfileView.TopChampion> toTopChampions(List<ProfileChampionStats> champions) {
        List<LolProfileView.TopChampion> response = new ArrayList<>();
        for (ProfileChampionStats champion : champions) {
            int totalGames = champion.wins() + champion.losses();
            response.add(new LolProfileView.TopChampion(
                champion.championId(),
                LolApiMapper.championName(champion.championId()),
                LolApiMapper.championImage(champion.championId()),
                champion.games(),
                champion.wins(),
                champion.losses(),
                LolApiMapper.rounded(LolApiMapper.ratio(champion.wins(), totalGames) * 100),
                LolApiMapper.rounded(champion.avgKills()),
                LolApiMapper.rounded(champion.avgDeaths()),
                LolApiMapper.rounded(champion.avgAssists()),
                avgKda(champion.avgKills(), champion.avgDeaths(), champion.avgAssists()),
                LolApiMapper.rounded(champion.avgCs()),
                champion.avgDamage(),
                champion.masteryLevel(),
                champion.masteryPoints()
            ));
        }
        return response;
    }

    private List<LolProfileView.RecentMatch> toRecentMatches(List<ProfileMatch> matches) {
        List<LolProfileView.RecentMatch> response = new ArrayList<>();
        for (ProfileMatch match : matches) {
            response.add(new LolProfileView.RecentMatch(
                match.gameId(),
                match.win(),
                match.win() ? "W" : "L",
                match.championId(),
                LolApiMapper.championName(match.championId()),
                LolApiMapper.championImage(match.championId()),
                match.lane(),
                match.kda(),
                kdaRatio(match.kda()),
                match.cs(),
                match.queue(),
                durationMs(match),
                LolApiMapper.duration(durationMs(match)),
                match.timeStart(),
                LolApiMapper.ago(match.timeStart()),
                match.damage(),
                match.gold(),
                match.vision(),
                match.items(),
                match.summonerSpells()
            ));
        }
        return response;
    }

    private double avgKda(double kills, double deaths, double assists) {
        return deaths > 0 ? LolApiMapper.rounded((kills + assists) / deaths) : LolApiMapper.rounded(kills + assists);
    }

    private double kdaRatio(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        String[] parts = kda.split("/");
        if (parts.length != 3) return 0;
        int kills = parseInt(parts[0]);
        int deaths = parseInt(parts[1]);
        int assists = parseInt(parts[2]);
        return deaths > 0 ? LolApiMapper.rounded((double) (kills + assists) / deaths) : kills + assists;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private long durationMs(ProfileMatch match) {
        return match.timeEnd() > match.timeStart() ? match.timeEnd() - match.timeStart() : 0;
    }

    private String profileIcon(int icon) {
        return "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/" + icon + ".jpg";
    }

    private record RiotId(String name, String tag) {
        static RiotId parse(String value) {
            if (value == null || value.isBlank()) {
                return new RiotId("", "");
            }
            String[] parts = value.split("#", 2);
            return new RiotId(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }
}
