// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.List;

import com.R4B.lol.*;

public class LevelTip implements java.io.Serializable
{
    private static final long serialVersionUID = 2379847783016336514L;
    
    private List<String> effect;
    private List<String> label;
    
    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (this.getClass() != obj.getClass())
        {
            return false;
        }
        final LevelTip other = (LevelTip) obj;
        if (this.effect == null)
        {
            if (other.effect != null)
            {
                return false;
            }
        } else if (!this.effect.equals(other.effect))
        {
            return false;
        }
        if (this.label == null)
        {
            return other.label == null;
        } else
        {
            return this.label.equals(other.label);
        }
    }

    public List<String> getEffect()
    {
        return this.effect;
    }

    public List<String> getLabel()
    {
        return this.label;
    }
    
    @Override
    public int hashCode()
    {
        final int prime  = 31;
        int       result = 1;
        result = (prime * result) + ((this.effect == null) ? 0 : this.effect.hashCode());
        result = (prime * result) + ((this.label == null) ? 0 : this.label.hashCode());
        return result;
    }
    
    @Override
    public String toString()
    {
        return "LevelTip{" +
               "effect=" + effect +
               ", label=" + label +
               '}';
    }
}
