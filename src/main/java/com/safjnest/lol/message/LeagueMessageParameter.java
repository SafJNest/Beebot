package com.safjnest.lol.message;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.SeasonUtils;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class LeagueMessageParameter {
    private LeagueMessageType messageType;
    private StaticChampion champion;
    private boolean showChampion;
    private int offset;
    private String selectedMatchId;
    private StringSelectMenu livegameMenu;
    private StringSelectMenu opggMenu;
    private Filter filter;

    public LeagueMessageParameter(LeagueMessageType messageType) {
        this.messageType = normalize(messageType);
        this.filter = defaultFilter(this.messageType);
    }

    public LeagueMessageParameter(LeagueMessageType messageType, Filter filter) {
        this.messageType = normalize(messageType);
        this.filter = filter != null ? filter : defaultFilter(this.messageType);
        applyFilterState(this.filter);
    }

    public LeagueMessageParameter(
        LeagueMessageType messageType,
        long[] period,
        GameQueueType queueType,
        LaneType laneType,
        StaticChampion champion,
        boolean showChampion,
        int offset
    ) {
        this.messageType = normalize(messageType);
        this.filter = defaultFilter(this.messageType).setPeriod(period).setQueue(queueType).setLane(laneType);
        this.champion = champion;
        this.showChampion = showChampion;
        this.offset = offset;
        toFilter();
    }

    public LeagueMessageParameter(List<Button> buttons) {
        this.messageType = LeagueMessageType.OVERVIEW;
        this.filter = defaultFilter(this.messageType);
        String prefix = LeagueMessage.BUTTON_ID_PREFIX;
        String timeString = "current";
        int fallbackChampion = 0;

        for (Button button : buttons) {
            boolean active = button.getStyle() == ButtonStyle.SUCCESS;
            String[] parts = button.getCustomId().split("-");
            String value = parts.length == 2 ? parts[1] : parts[2];
            if (button.getCustomId().startsWith(prefix + "-queue-") && active) {
                try { filter.setQueue(GameQueueType.valueOf(value)); }
                catch (RuntimeException ignored) { }
            }
            if (button.getCustomId().startsWith(prefix + "-type-") && active) {
                try { setMessageType(LeagueMessageType.valueOf(value.toUpperCase())); }
                catch (RuntimeException ignored) { }
            }
            if (button.getCustomId().startsWith(prefix + "-lane-") && active) {
                try { filter.setLane(LaneType.valueOf(value)); }
                catch (RuntimeException ignored) { }
            }
            if (button.getCustomId().startsWith(prefix + "-champion-")) {
                champion = ChampionUtils.getChampion(Integer.parseInt(value));
                showChampion = active;
            }
            if (button.getCustomId().startsWith(prefix + "-season-") && active) timeString = value;
            if (button.getCustomId().startsWith(prefix + "-leftpage")) {
                String[] pageParts = button.getCustomId().split("-", 4);
                offset = Integer.parseInt(value);
                if (pageParts.length == 4) {
                    try { applyFilter(Filter.fromStateKey(pageParts[3])); }
                    catch (RuntimeException ignored) { }
                }
            }
            if (button.getCustomId().startsWith(prefix + "-change")) fallbackChampion = Integer.parseInt(value);
        }

        if (champion == null && fallbackChampion > 0) champion = ChampionUtils.getChampion(fallbackChampion);
        filter.setPeriod(switch (timeString) {
            case "all" -> new long[] {0, 0};
            case "previous" -> SeasonUtils.getPreviousSplitRange();
            default -> SeasonUtils.getCurrentSplitRange();
        });
        toFilter();
    }

    public LeagueMessageParameter withComponents(List<StringSelectMenu> menus) {
        for (StringSelectMenu menu : menus) {
            if (menu.getCustomId().equals(LeagueMessage.BUTTON_ID_PREFIX + "-opggselect")) opggMenu = menu;
            if (menu.getCustomId().equals(LeagueMessage.BUTTON_ID_PREFIX + "-rankselect")) livegameMenu = menu;
        }
        return this;
    }

    private static Filter defaultFilter(LeagueMessageType messageType) {
        if (messageType == LeagueMessageType.PROFILE || messageType == LeagueMessageType.OVERVIEW
            || messageType == LeagueMessageType.MATCHUP || messageType == LeagueMessageType.OVERVIEW_CHAMPIONS
            || messageType == LeagueMessageType.OVERVIEW_OPGG) {
            return Filter.summoner();
        }
        return new Filter();
    }

    private static LeagueMessageType normalize(LeagueMessageType messageType) {
        return messageType == LeagueMessageType.OVERVIEW_PING || messageType == LeagueMessageType.OVERVIEW_OBJECTIVES
            ? LeagueMessageType.OVERVIEW : messageType;
    }

    private void applyFilter(Filter value) {
        filter = value != null ? value : defaultFilter(messageType);
        applyFilterState(filter);
    }

    private void applyFilterState(Filter value) {
        if (value.champion() != 0) {
            champion = ChampionUtils.getChampion(value.champion());
            showChampion = true;
        }
    }

    public LeagueMessageType getMessageType() { return messageType; }

    public void setMessageType(LeagueMessageType messageType) { this.messageType = normalize(messageType); }

    public long[] getPeriod() { return filter.period(); }

    public void setPeriod(long[] period) { filter.setPeriod(period); }

    public GameQueueType getQueueType() { return filter.queue(); }

    public void setQueueType(GameQueueType queueType) { filter.setQueue(queueType); }

    public LaneType getLaneType() { return filter.lane(); }

    public void setLaneType(LaneType laneType) { filter.setLane(laneType); }

    public StaticChampion getChampion() { return champion; }

    public void setChampion(StaticChampion champion) {
        this.champion = champion;
        filter.setChampion(champion == null ? 0 : champion.getId());
    }

    public boolean isShowChampion() { return showChampion; }

    public void setShowChampion(boolean showChampion) { this.showChampion = showChampion; }

    public int getOffset() { return offset; }

    public void setOffset(int offset) { this.offset = offset; }

    public int getChampionId() { return champion != null ? champion.getId() : 0; }

    public int getOpponent() { return filter.opponent(); }

    public void setOpponent(int opponent) { filter.setOpponent(opponent); }

    public int getDuo() { return filter.duo(); }

    public void setDuo(int duo) { filter.setDuo(duo); }

    public int getShowingChampion() { return showChampion ? getChampionId() : 0; }

    public boolean isDuo() {
        return filter.lane() == LaneType.BOT || filter.lane() == LaneType.UTILITY || GameQueueTypeUtils.isCherry(filter.queue());
    }

    public long getTimeStart() { return filter.timeStart(); }

    public long getTimeEnd() { return filter.timeEnd(); }

    public void setSelectedMatchId(String selectedMatchId) { this.selectedMatchId = selectedMatchId; }

    public String getSelectedMatchId() { return selectedMatchId; }

    public StringSelectMenu getLivegameMenu() { return livegameMenu; }

    public StringSelectMenu getOpggMenu() { return opggMenu; }

    public String getPatch() { return filter.patch(); }

    public void setPatch(String patch) { filter.setPatch(patch); }

    public TierType getRank() { return filter.rank(); }

    public void setRank(TierType rank) { filter.setRank(rank); }

    public LeagueShard getRegion() { return filter.region(); }

    public void setRegion(LeagueShard region) { filter.setRegion(region); }

    public Filter toFilter() {
        return filter.setChampion(showChampion ? getChampionId() : 0);
    }
}
