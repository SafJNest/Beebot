package com.safjnest.lol.model;

import java.util.List;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class SummonerData {

    private String _id;
    private String riot_id;
    private String puuid;
    private LeagueShard region;
    private int level;
    private int icon;
    private boolean tracking;
    private String userId;
    private List<RankData> ranked;
    private List<MasteryData> masteries;

    public String get_id() { return _id; }
    public void set_id(String _id) { this._id = _id; }

    public String getRiot_id() { return riot_id; }
    public void setRiot_id(String riot_id) { this.riot_id = riot_id; }

    public String getPuuid() { return puuid; }
    public void setPuuid(String puuid) { this.puuid = puuid; }

    public LeagueShard getRegion() { return region; }
    public void setRegion(LeagueShard region) { this.region = region; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getIcon() { return icon; }
    public void setIcon(int icon) { this.icon = icon; }

    public boolean isTracking() { return tracking; }
    public void setTracking(boolean tracking) { this.tracking = tracking; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<RankData> getRanked() { return ranked; }
    public void setRanked(List<RankData> ranked) { this.ranked = ranked; }

    public List<MasteryData> getMasteries() { return masteries; }
    public void setMasteries(List<MasteryData> masteries) { this.masteries = masteries; }

    @Override
    public String toString() {
        return "SummonerData{" +
                "_id='" + _id + '\'' +
                ", riot_id='" + riot_id + '\'' +
                ", puuid='" + puuid + '\'' +
                ", region=" + region +
                ", level=" + level +
                ", icon=" + icon +
                ", tracking=" + tracking +
                ", userId='" + userId + '\'' +
                ", ranked=" + ranked +
                ", masteries=" + masteries +
                '}';
    }
}
