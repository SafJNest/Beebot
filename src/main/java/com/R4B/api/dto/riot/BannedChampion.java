// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class BannedChampion implements java.io.Serializable
{
    private static final long serialVersionUID = -4431824998067471719L;
    
    private int championId;
    private int pickTurn;
    private int teamId;

    public int getChampionId()
    {
        return this.championId;
    }

    public int getPickTurn()
    {
        return this.pickTurn;
    }

    public int getTeamId()
    {
        return teamId;
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
        BannedChampion that = (BannedChampion) o;
        return championId == that.championId &&
               pickTurn == that.pickTurn &&
               teamId == that.teamId;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(championId, pickTurn, teamId);
    }
    
    @Override
    public String toString()
    {
        return "BannedChampion{" +
               "championId=" + championId +
               ", pickTurn=" + pickTurn +
               ", teamId=" + teamId +
               '}';
    }
}
