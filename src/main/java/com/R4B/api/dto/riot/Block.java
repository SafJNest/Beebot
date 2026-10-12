// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.List;

import com.R4B.lol.*;

public class Block implements java.io.Serializable
{
    private static final long serialVersionUID = -2065008460415100602L;
    
    private List<BlockItem> items;
    private boolean         recMath;
    private String          type;

    public List<BlockItem> getItems()
    {
        return this.items;
    }

    public boolean getRecMath()
    {
        return this.recMath;
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
        
        Block block = (Block) o;
        
        if (recMath != block.recMath)
        {
            return false;
        }
        if ((items != null) ? !items.equals(block.items) : (block.items != null))
        {
            return false;
        }
        return (type != null) ? type.equals(block.type) : (block.type == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = items != null ? items.hashCode() : 0;
        result = 31 * result + (recMath ? 1 : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString()
    {
        return "Block{" +
               "items=" + items +
               ", recMath=" + recMath +
               ", type='" + type + '\'' +
               '}';
    }
}
