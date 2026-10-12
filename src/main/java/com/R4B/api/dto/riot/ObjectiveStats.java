// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class ObjectiveStats implements java.io.Serializable
{
    private static final long serialVersionUID = -7533433019007054291L;
    
    private boolean first;
    private int     kills;
    
    public boolean isFirst()
    {
        return first;
    }
    
    public int getKills()
    {
        return kills;
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
        ObjectiveStats that = (ObjectiveStats) o;
        return first == that.first && kills == that.kills;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(first, kills);
    }
    
    @Override
    public String toString()
    {
        return "ObjectiveStat{" +
               "first=" + first +
               ", kills=" + kills +
               '}';
    }
}
