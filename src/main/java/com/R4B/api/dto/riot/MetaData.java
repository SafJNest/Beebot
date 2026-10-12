// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class MetaData implements java.io.Serializable
{
    private static final long serialVersionUID = 6882105003422963719L;
    
    private boolean isRune;
    private String  tier;
    private String  type;

    public boolean getIsRune()
    {
        return this.isRune;
    }

    public String getTier()
    {
        return this.tier;
    }

    public String getType()
    {
        return this.type;
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
        
        MetaData metaData = (MetaData) o;
        
        if (isRune != metaData.isRune)
        {
            return false;
        }
        if ((tier != null) ? !tier.equals(metaData.tier) : (metaData.tier != null))
        {
            return false;
        }
        return (type != null) ? type.equals(metaData.type) : (metaData.type == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = isRune ? 1 : 0;
        result = 31 * result + (tier != null ? tier.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString()
    {
        return "MetaData{" +
               "isRune=" + isRune +
               ", tier='" + tier + '\'' +
               ", type='" + type + '\'' +
               '}';
    }
}
