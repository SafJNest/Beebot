// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.util.List;

import com.R4B.lol.*;

public class StaticSummonerSpell extends BaseSpellData
{
    private static final long serialVersionUID = 6564283228389342174L;
    
    private int          id;
    private List<String> modes;
    private int          summonerLevel;

    public int getId()
    {
        return this.id;
    }

    public List<String> getModes()
    {
        return this.modes;
    }

    public int getSummonerLevel()
    {
        return this.summonerLevel;
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
        
        StaticSummonerSpell that = (StaticSummonerSpell) o;
        
        if (id != that.id)
        {
            return false;
        }
        if (summonerLevel != that.summonerLevel)
        {
            return false;
        }
        return (modes != null) ? modes.equals(that.modes) : (that.modes == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = id;
        result = 31 * result + (modes != null ? modes.hashCode() : 0);
        result = 31 * result + summonerLevel;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "StaticSummonerSpell{" +
               "id=" + id +
               ", modes=" + modes +
               ", summonerLevel=" + summonerLevel +
               '}';
    }
}
