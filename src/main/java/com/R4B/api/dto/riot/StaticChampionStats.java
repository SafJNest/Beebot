// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class StaticChampionStats implements java.io.Serializable
{
    private static final long serialVersionUID = 8763100806023208221L;
    
    private float armor;
    private float armorperlevel;
    private float attackdamage;
    private float attackdamageperlevel;
    private float attackrange;
    private float attackspeed;
    private float attackspeedperlevel;
    private float crit;
    private float critperlevel;
    private float hp;
    private float hpperlevel;
    private float hpregen;
    private float hpregenperlevel;
    private float movespeed;
    private float mp;
    private float mpperlevel;
    private float mpregen;
    private float mpregenperlevel;
    private float spellblock;
    private float spellblockperlevel;

    public float getArmor()
    {
        return this.armor;
    }

    public float getArmorperlevel()
    {
        return this.armorperlevel;
    }

    public float getAttackdamage()
    {
        return this.attackdamage;
    }

    public float getAttackdamageperlevel()
    {
        return this.attackdamageperlevel;
    }

    public float getAttackrange()
    {
        return this.attackrange;
    }

    public float getAttackspeed()
    {
        return this.attackspeed;
    }

    public float getAttackspeedperlevel()
    {
        return this.attackspeedperlevel;
    }

    public float getCrit()
    {
        return this.crit;
    }

    public float getCritperlevel()
    {
        return this.critperlevel;
    }

    public float getHp()
    {
        return this.hp;
    }

    public float getHpperlevel()
    {
        return this.hpperlevel;
    }

    public float getHpregen()
    {
        return this.hpregen;
    }

    public float getHpregenperlevel()
    {
        return this.hpregenperlevel;
    }

    public float getMovespeed()
    {
        return this.movespeed;
    }

    public float getMp()
    {
        return this.mp;
    }

    public float getMpperlevel()
    {
        return this.mpperlevel;
    }

    public float getMpregen()
    {
        return this.mpregen;
    }

    public float getMpregenperlevel()
    {
        return this.mpregenperlevel;
    }

    public float getSpellblock()
    {
        return this.spellblock;
    }

    public float getSpellblockperlevel()
    {
        return this.spellblockperlevel;
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
        
        StaticChampionStats stats = (StaticChampionStats) o;
        
        if (Float.compare(stats.armor, armor) != 0)
        {
            return false;
        }
        if (Float.compare(stats.armorperlevel, armorperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.attackdamage, attackdamage) != 0)
        {
            return false;
        }
        if (Float.compare(stats.attackdamageperlevel, attackdamageperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.attackrange, attackrange) != 0)
        {
            return false;
        }
        if (Float.compare(stats.attackspeed, attackspeed) != 0)
        {
            return false;
        }
        if (Float.compare(stats.attackspeedperlevel, attackspeedperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.crit, crit) != 0)
        {
            return false;
        }
        if (Float.compare(stats.critperlevel, critperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.hp, hp) != 0)
        {
            return false;
        }
        if (Float.compare(stats.hpperlevel, hpperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.hpregen, hpregen) != 0)
        {
            return false;
        }
        if (Float.compare(stats.hpregenperlevel, hpregenperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.movespeed, movespeed) != 0)
        {
            return false;
        }
        if (Float.compare(stats.mp, mp) != 0)
        {
            return false;
        }
        if (Float.compare(stats.mpperlevel, mpperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.mpregen, mpregen) != 0)
        {
            return false;
        }
        if (Float.compare(stats.mpregenperlevel, mpregenperlevel) != 0)
        {
            return false;
        }
        if (Float.compare(stats.spellblock, spellblock) != 0)
        {
            return false;
        }
        return Float.compare(stats.spellblockperlevel, spellblockperlevel) == 0;
    }
    
    @Override
    public int hashCode()
    {
        int result = armor != +0.0f ? Float.floatToIntBits(armor) : 0;
        result = 31 * result + (armorperlevel != +0.0f ? Float.floatToIntBits(armorperlevel) : 0);
        result = 31 * result + (attackdamage != +0.0f ? Float.floatToIntBits(attackdamage) : 0);
        result = 31 * result + (attackdamageperlevel != +0.0f ? Float.floatToIntBits(attackdamageperlevel) : 0);
        result = 31 * result + (attackrange != +0.0f ? Float.floatToIntBits(attackrange) : 0);
        result = 31 * result + (attackspeed != +0.0f ? Float.floatToIntBits(attackspeed) : 0);
        result = 31 * result + (attackspeedperlevel != +0.0f ? Float.floatToIntBits(attackspeedperlevel) : 0);
        result = 31 * result + (crit != +0.0f ? Float.floatToIntBits(crit) : 0);
        result = 31 * result + (critperlevel != +0.0f ? Float.floatToIntBits(critperlevel) : 0);
        result = 31 * result + (hp != +0.0f ? Float.floatToIntBits(hp) : 0);
        result = 31 * result + (hpperlevel != +0.0f ? Float.floatToIntBits(hpperlevel) : 0);
        result = 31 * result + (hpregen != +0.0f ? Float.floatToIntBits(hpregen) : 0);
        result = 31 * result + (hpregenperlevel != +0.0f ? Float.floatToIntBits(hpregenperlevel) : 0);
        result = 31 * result + (movespeed != +0.0f ? Float.floatToIntBits(movespeed) : 0);
        result = 31 * result + (mp != +0.0f ? Float.floatToIntBits(mp) : 0);
        result = 31 * result + (mpperlevel != +0.0f ? Float.floatToIntBits(mpperlevel) : 0);
        result = 31 * result + (mpregen != +0.0f ? Float.floatToIntBits(mpregen) : 0);
        result = 31 * result + (mpregenperlevel != +0.0f ? Float.floatToIntBits(mpregenperlevel) : 0);
        result = 31 * result + (spellblock != +0.0f ? Float.floatToIntBits(spellblock) : 0);
        result = 31 * result + (spellblockperlevel != +0.0f ? Float.floatToIntBits(spellblockperlevel) : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "StaticChampionStats{" +
               "armor=" + armor +
               ", armorperlevel=" + armorperlevel +
               ", attackdamage=" + attackdamage +
               ", attackdamageperlevel=" + attackdamageperlevel +
               ", attackrange=" + attackrange +
               ", attackspeed=" + attackspeed +
               ", attackspeedperlevel=" + attackspeedperlevel +
               ", crit=" + crit +
               ", critperlevel=" + critperlevel +
               ", hp=" + hp +
               ", hpperlevel=" + hpperlevel +
               ", hpregen=" + hpregen +
               ", hpregenperlevel=" + hpregenperlevel +
               ", movespeed=" + movespeed +
               ", mp=" + mp +
               ", mpperlevel=" + mpperlevel +
               ", mpregen=" + mpregen +
               ", mpregenperlevel=" + mpregenperlevel +
               ", spellblock=" + spellblock +
               ", spellblockperlevel=" + spellblockperlevel +
               '}';
    }
}
