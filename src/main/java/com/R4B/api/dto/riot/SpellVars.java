// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

@SuppressWarnings("unchecked")
public class SpellVars implements java.io.Serializable
{
    private static final long serialVersionUID = -8372973077273055306L;
    
    private Object coeff;
    private String dyn;
    private String key;
    private String link;
    private String ranksWith;

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
        final SpellVars other = (SpellVars) obj;
        if (this.coeff == null)
        {
            if (other.coeff != null)
            {
                return false;
            }
        } else if (!this.coeff.equals(other.coeff))
        {
            return false;
        }
        if (this.dyn == null)
        {
            if (other.dyn != null)
            {
                return false;
            }
        } else if (!this.dyn.equals(other.dyn))
        {
            return false;
        }
        if (this.key == null)
        {
            if (other.key != null)
            {
                return false;
            }
        } else if (!this.key.equals(other.key))
        {
            return false;
        }
        if (this.link == null)
        {
            if (other.link != null)
            {
                return false;
            }
        } else if (!this.link.equals(other.link))
        {
            return false;
        }
        if (this.ranksWith == null)
        {
            return other.ranksWith == null;
        } else
        {
            return this.ranksWith.equals(other.ranksWith);
        }
    }

    public List<Double> getCoeff()
    {
        return this.coeff instanceof List ? (List<Double>) this.coeff : Collections.singletonList((Double) coeff);
    }

    public String getDyn()
    {
        return this.dyn;
    }

    public String getKey()
    {
        return this.key;
    }

    public String getLink()
    {
        return this.link;
    }

    public String getRanksWith()
    {
        return this.ranksWith;
    }
    
    @Override
    public int hashCode()
    {
        final int prime  = 31;
        int       result = 1;
        result = (prime * result) + ((this.coeff == null) ? 0 : this.coeff.hashCode());
        result = (prime * result) + ((this.dyn == null) ? 0 : this.dyn.hashCode());
        result = (prime * result) + ((this.key == null) ? 0 : this.key.hashCode());
        result = (prime * result) + ((this.link == null) ? 0 : this.link.hashCode());
        result = (prime * result) + ((this.ranksWith == null) ? 0 : this.ranksWith.hashCode());
        return result;
    }
    
    @Override
    public String toString()
    {
        return "SpellVars{" +
               "coeff=" + coeff +
               ", dyn='" + dyn + '\'' +
               ", key='" + key + '\'' +
               ", link='" + link + '\'' +
               ", ranksWith='" + ranksWith + '\'' +
               '}';
    }
}
