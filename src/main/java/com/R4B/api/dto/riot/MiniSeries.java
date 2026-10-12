// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class MiniSeries implements java.io.Serializable
{
    private static final long serialVersionUID = 6390959392928565281L;
    
    private int    losses;
    private String progress;
    private int    target;
    private int    wins;

    public int getLosses()
    {
        return this.losses;
    }

    public String getProgress()
    {
        return this.progress;
    }

    public int getTarget()
    {
        return this.target;
    }

    public int getWins()
    {
        return this.wins;
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
        
        MiniSeries that = (MiniSeries) o;
        
        if (losses != that.losses)
        {
            return false;
        }
        if (target != that.target)
        {
            return false;
        }
        if (wins != that.wins)
        {
            return false;
        }
        return (progress != null) ? progress.equals(that.progress) : (that.progress == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = losses;
        result = 31 * result + (progress != null ? progress.hashCode() : 0);
        result = 31 * result + target;
        result = 31 * result + wins;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "MiniSeries{" +
               "losses=" + losses +
               ", progress='" + progress + '\'' +
               ", target=" + target +
               ", wins=" + wins +
               '}';
    }
}
