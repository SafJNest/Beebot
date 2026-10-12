// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.util.*;

import com.R4B.lol.*;

public class StaticChampionSpell extends BaseSpellData
{
    private static final long serialVersionUID = 3332883769496451613L;
    
    private List<Image> altimages;
    
    public List<Image> getAltImages()
    {
        return altimages;
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
        if (!super.equals(o))
        {
            return false;
        }
        
        StaticChampionSpell that = (StaticChampionSpell) o;
        
        return Objects.equals(altimages, that.altimages);
    }
    
    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (altimages != null ? altimages.hashCode() : 0);
        return result;
    }
}
