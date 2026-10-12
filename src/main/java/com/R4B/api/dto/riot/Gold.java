// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class Gold implements java.io.Serializable
{
    private static final long serialVersionUID = -7870414403107823868L;
    
    private int     base;
    private boolean purchasable;
    private int     sell;
    private int     total;

    public int getBase()
    {
        return this.base;
    }

    public boolean getPurchasable()
    {
        return this.purchasable;
    }

    public int getSell()
    {
        return this.sell;
    }

    public int getTotal()
    {
        return this.total;
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
        
        Gold gold = (Gold) o;
        
        if (base != gold.base)
        {
            return false;
        }
        if (purchasable != gold.purchasable)
        {
            return false;
        }
        if (sell != gold.sell)
        {
            return false;
        }
        return total == gold.total;
    }
    
    @Override
    public int hashCode()
    {
        int result = base;
        result = 31 * result + (purchasable ? 1 : 0);
        result = 31 * result + sell;
        result = 31 * result + total;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "Gold{" +
               "base=" + base +
               ", purchasable=" + purchasable +
               ", sell=" + sell +
               ", total=" + total +
               '}';
    }
}
