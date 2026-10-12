// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.List;

import com.R4B.lol.*;

public class SpectatorPerks implements java.io.Serializable
{
    private static final long serialVersionUID = 7928938954384751658L;
    
    private List<Integer> perkIds;
    private int           perkStyle;
    private int           perkSubStyle;
    
    public List<Integer> getPerkIds()
    {
        return perkIds;
    }
    
    public int getPerkStyle()
    {
        return perkStyle;
    }
    
    public int getPerkSubStyle()
    {
        return perkSubStyle;
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
        
        SpectatorPerks that = (SpectatorPerks) o;
        
        if (perkStyle != that.perkStyle)
        {
            return false;
        }
        if (perkSubStyle != that.perkSubStyle)
        {
            return false;
        }
        return (perkIds != null) ? perkIds.equals(that.perkIds) : (that.perkIds == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = perkIds != null ? perkIds.hashCode() : 0;
        result = 31 * result + perkStyle;
        result = 31 * result + perkSubStyle;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "SpectatorPerks{" +
               "perkIds=" + perkIds +
               ", perkStyle=" + perkStyle +
               ", perkSubStyle=" + perkSubStyle +
               '}';
    }
}
