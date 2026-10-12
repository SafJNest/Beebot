// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class SpectatorParticipant implements java.io.Serializable
{
    private static final long serialVersionUID = -4280093223886492229L;
    
    private int                                    championId;
    private long                                   profileIconId;
    private SummonerSpellType                      spell1Id;
    private SummonerSpellType                      spell2Id;
    private String                                 summonerId;
    private String                                 puuid;
    private String                                 riotId;
    private TeamType                               teamId;
    private boolean                                bot;
    private SpectatorPerks                         perks;
    private List<SpectatorGameCustomizationObject> gameCustomizationObjects;

    public int getChampionId()
    {
        return championId;
    }

    public long getProfileIconId()
    {
        return profileIconId;
    }

    public SummonerSpellType getSpell1()
    {
        return spell1Id;
    }

    public SummonerSpellType getSpell2()
    {
        return spell2Id;
    }

    public TeamType getTeam()
    {
        return teamId;
    }

    public boolean isBot()
    {
        return bot;
    }

    public String getSummonerId()
    {
        return summonerId;
    }

    public SpectatorPerks getPerks()
    {
        return perks;
    }

    public List<SpectatorGameCustomizationObject> getGameCustomizationObjects()
    {
        return gameCustomizationObjects;
    }
    
    public String getPuuid()
    {
        return puuid;
    }
    
    public String getRiotId()
    {
        return riotId;
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
        SpectatorParticipant that = (SpectatorParticipant) o;
        return championId == that.championId
               && profileIconId == that.profileIconId
               && bot == that.bot
               && spell1Id == that.spell1Id
               && spell2Id == that.spell2Id
               && Objects.equals(summonerId, that.summonerId)
               && Objects.equals(puuid, that.puuid)
               && Objects.equals(riotId, that.riotId)
               && teamId == that.teamId
               && Objects.equals(perks, that.perks)
               && Objects.equals(gameCustomizationObjects, that.gameCustomizationObjects);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(championId, profileIconId, spell1Id, spell2Id, summonerId, puuid, riotId, teamId, bot, perks, gameCustomizationObjects);
    }
    
    @Override
    public String toString()
    {
        return "SpectatorParticipant{" +
               "championId=" + championId +
               ", profileIconId=" + profileIconId +
               ", spell1Id=" + spell1Id +
               ", spell2Id=" + spell2Id +
               ", summonerId='" + summonerId + '\'' +
               ", puuid='" + puuid + '\'' +
               ", riotId='" + riotId + '\'' +
               ", teamId=" + teamId +
               ", bot=" + bot +
               ", perks=" + perks +
               ", gameCustomizationObjects=" + gameCustomizationObjects +
               '}';
    }
}
