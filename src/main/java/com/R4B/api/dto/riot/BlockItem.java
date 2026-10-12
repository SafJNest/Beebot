// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class BlockItem implements java.io.Serializable
{
    
    private static final long serialVersionUID = -7656361974613593574L;
    
    private int count;
    private int id;

    public int getCount()
    {
        return this.count;
    }

    public int getId()
    {
        return this.id;
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
        
        BlockItem blockItem = (BlockItem) o;
        
        if (count != blockItem.count)
        {
            return false;
        }
        return id == blockItem.id;
    }
    
    @Override
    public int hashCode()
    {
        int result = count;
        result = 31 * result + id;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "BlockItem{" +
               "count=" + count +
               ", id=" + id +
               '}';
    }
}
