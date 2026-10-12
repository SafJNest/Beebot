// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class Skin implements java.io.Serializable
{
    private static final long serialVersionUID = 6490718203654409943L;
    
    private int    id;
    private String name;
    private int    num;

    public int getId()
    {
        return this.id;
    }

    public String getName()
    {
        return this.name;
    }

    public int getNum()
    {
        return this.num;
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
        
        Skin skin = (Skin) o;
        
        if (id != skin.id)
        {
            return false;
        }
        if (num != skin.num)
        {
            return false;
        }
        return (name != null) ? name.equals(skin.name) : (skin.name == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = id;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + num;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "Skin{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", num=" + num +
               '}';
    }
}
