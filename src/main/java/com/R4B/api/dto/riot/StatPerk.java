// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class StatPerk implements java.io.Serializable
{
    private static final long serialVersionUID = 5341158852307028886L;
    
    private int defense;
    private int flex;
    private int offense;
    
    public int getDefense()
    {
        return defense;
    }
    
    public int getFlex()
    {
        return flex;
    }
    
    public int getOffense()
    {
        return offense;
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
        StatPerk statPerk = (StatPerk) o;
        return defense == statPerk.defense && flex == statPerk.flex && offense == statPerk.offense;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(defense, flex, offense);
    }
    
    @Override
    public String toString()
    {
        return "StatPerk{" +
               "defense=" + defense +
               ", flex=" + flex +
               ", offense=" + offense +
               '}';
    }
}
