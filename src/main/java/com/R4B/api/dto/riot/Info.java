// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class Info implements java.io.Serializable
{
    private static final long serialVersionUID = -6152240607837015048L;
    
    private int attack;
    private int defense;
    private int difficulty;
    private int magic;

    public int getAttack()
    {
        return this.attack;
    }

    public int getDefense()
    {
        return this.defense;
    }

    public int getDifficulty()
    {
        return this.difficulty;
    }

    public int getMagic()
    {
        return this.magic;
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
        
        Info info = (Info) o;
        
        if (attack != info.attack)
        {
            return false;
        }
        if (defense != info.defense)
        {
            return false;
        }
        if (difficulty != info.difficulty)
        {
            return false;
        }
        return magic == info.magic;
    }
    
    @Override
    public int hashCode()
    {
        int result = attack;
        result = 31 * result + defense;
        result = 31 * result + difficulty;
        result = 31 * result + magic;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "Info{" +
               "attack=" + attack +
               ", defense=" + defense +
               ", difficulty=" + difficulty +
               ", magic=" + magic +
               '}';
    }
}
