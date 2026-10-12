// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class MatchPerks implements java.io.Serializable
{
    private static final long serialVersionUID = -8424250372683028377L;
    
    private StatPerk        statPerks;
    private List<PerkStyle> styles;
    
    public StatPerk getStatPerks()
    {
        return statPerks;
    }
    
    public List<PerkStyle> getPerkStyles()
    {
        return styles;
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
        MatchPerks that = (MatchPerks) o;
        return Objects.equals(statPerks, that.statPerks) && Objects.equals(styles, that.styles);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(statPerks, styles);
    }
    
    @Override
    public String toString()
    {
        return "MatchPerks{" +
               "statPerks=" + statPerks +
               ", perkStyles=" + styles +
               '}';
    }
}
