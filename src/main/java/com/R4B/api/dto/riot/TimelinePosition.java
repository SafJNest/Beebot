// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class TimelinePosition implements java.io.Serializable
{
    private static final long serialVersionUID = 7605395081863390628L;
    
    private int x;
    private int y;

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
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
        
        TimelinePosition that = (TimelinePosition) o;
        
        if (x != that.x)
        {
            return false;
        }
        return y == that.y;
    }
    
    @Override
    public int hashCode()
    {
        int result = x;
        result = 31 * result + y;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "MatchPosition{" +
               "x=" + x +
               ", y=" + y +
               '}';
    }
}
