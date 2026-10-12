// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class LeagueItem implements java.io.Serializable
{
    private static final long serialVersionUID = -6639741183354914597L;
    
    protected String     rank;
    protected boolean    hotStreak;
    protected MiniSeries miniSeries;
    protected int        wins;
    protected boolean    veteran;
    protected int        losses;
    protected String     summonerId;
    protected String     puuid;
    protected boolean    inactive;
    protected boolean    freshBlood;
    protected int        leaguePoints;

    public TierDivisionType getTierDivisionType(TierType tier)
    {
        return TierDivisionType.getFromCodes(tier.getValue(), rank);
    }

    public String getRank()
    {
        return this.rank;
    }

    public boolean getIsFreshBlood()
    {
        return this.freshBlood;
    }

    public boolean getIsHotStreak()
    {
        return this.hotStreak;
    }

    public boolean getIsInactive()
    {
        return this.inactive;
    }

    public boolean getIsVeteran()
    {
        return this.veteran;
    }

    public int getLeaguePoints()
    {
        return this.leaguePoints;
    }

    public int getLosses()
    {
        return this.losses;
    }

    public MiniSeries getMiniSeries()
    {
        return this.miniSeries;
    }
    
    public boolean isInPromos()
    {
        return miniSeries != null;
    }

    public String getSummonerId()
    {
        return this.summonerId;
    }

    public String getPuuid() {
      return this.puuid;
    }

    public int getWins()
    {
        return this.wins;
    }
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        LeagueItem that = (LeagueItem) o;
        return hotStreak == that.hotStreak &&
               wins == that.wins &&
               veteran == that.veteran &&
               losses == that.losses &&
               inactive == that.inactive &&
               freshBlood == that.freshBlood &&
               leaguePoints == that.leaguePoints &&
               Objects.equals(rank, that.rank) &&
               Objects.equals(miniSeries, that.miniSeries) &&
               Objects.equals(summonerId, that.summonerId);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(rank, hotStreak, miniSeries, wins, veteran, losses, summonerId, inactive, freshBlood, leaguePoints);
    }
    
    @Override
    public String toString()
    {
        return "LeagueItem{" +
               "rank='" + rank + '\'' +
               ", freshBlood=" + freshBlood +
               ", hotStreak=" + hotStreak +
               ", inactive=" + inactive +
               ", veteran=" + veteran +
               ", leaguePoints=" + leaguePoints +
               ", losses=" + losses +
               ", miniSeries=" + miniSeries +
               ", summonerId='" + summonerId + '\'' +
               ", puuid='" + puuid + '\'' +
               ", wins=" + wins +
               '}';
    }
}
