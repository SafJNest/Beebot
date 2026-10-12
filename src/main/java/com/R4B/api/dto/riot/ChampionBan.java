// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class ChampionBan implements java.io.Serializable
{
    private static final long serialVersionUID = 9036737379955122323L;
    
    private int championId;
    private int pickTurn;
    
    public int getChampionId()
    {
        return championId;
    }
    
    public int getPickTurn()
    {
        return pickTurn;
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
        ChampionBan that = (ChampionBan) o;
        return championId == that.championId && pickTurn == that.pickTurn;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(championId, pickTurn);
    }
    
    @Override
    public String toString()
    {
        return "ChampionBan{" +
               "championId=" + championId +
               ", pickTurn=" + pickTurn +
               '}';
    }
}
