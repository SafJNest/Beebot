// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class InventoryDataStats implements java.io.Serializable
{
    private static final long serialVersionUID = 420709117265668274L;
    
    private float FlatArmorMod;
    private float FlatAttackSpeedMod;
    private float FlatBlockMod;
    private float FlatCritChanceMod;
    private float FlatCritDamageMod;
    private float FlatEXPBonus;
    private float FlatEnergyPoolMod;
    private float FlatEnergyRegenMod;
    private float FlatHPPoolMod;
    private float FlatHPRegenMod;
    private float FlatMPPoolMod;
    private float FlatMPRegenMod;
    private float FlatMagicDamageMod;
    private float FlatMovementSpeedMod;
    private float FlatPhysicalDamageMod;
    private float FlatSpellBlockMod;
    private float PercentArmorMod;
    private float PercentAttackSpeedMod;
    private float PercentBlockMod;
    private float PercentCritChanceMod;
    private float PercentCritDamageMod;
    private float PercentDodgeMod;
    private float PercentEXPBonus;
    private float PercentHPPoolMod;
    private float PercentHPRegenMod;
    private float PercentLifeStealMod;
    private float PercentMPPoolMod;
    private float PercentMPRegenMod;
    private float PercentMagicDamageMod;
    private float PercentMovementSpeedMod;
    private float PercentPhysicalDamageMod;
    private float PercentSpellBlockMod;
    private float PercentSpellVampMod;

    public float getFlatArmorMod()
    {
        return this.FlatArmorMod;
    }

    public float getFlatAttackSpeedMod()
    {
        return this.FlatAttackSpeedMod;
    }

    public float getFlatBlockMod()
    {
        return this.FlatBlockMod;
    }

    public float getFlatCritChanceMod()
    {
        return this.FlatCritChanceMod;
    }

    public float getFlatCritDamageMod()
    {
        return this.FlatCritDamageMod;
    }

    public float getFlatEnergyPoolMod()
    {
        return this.FlatEnergyPoolMod;
    }

    public float getFlatEnergyRegenMod()
    {
        return this.FlatEnergyRegenMod;
    }

    public float getFlatEXPBonus()
    {
        return this.FlatEXPBonus;
    }

    public float getFlatHPPoolMod()
    {
        return this.FlatHPPoolMod;
    }

    public float getFlatHPRegenMod()
    {
        return this.FlatHPRegenMod;
    }

    public float getFlatMagicDamageMod()
    {
        return this.FlatMagicDamageMod;
    }

    public float getFlatMovementSpeedMod()
    {
        return this.FlatMovementSpeedMod;
    }

    public float getFlatMPPoolMod()
    {
        return this.FlatMPPoolMod;
    }

    public float getFlatMPRegenMod()
    {
        return this.FlatMPRegenMod;
    }

    public float getFlatPhysicalDamageMod()
    {
        return this.FlatPhysicalDamageMod;
    }

    public float getFlatSpellBlockMod()
    {
        return this.FlatSpellBlockMod;
    }

    public float getPercentArmorMod()
    {
        return this.PercentArmorMod;
    }

    public float getPercentAttackSpeedMod()
    {
        return this.PercentAttackSpeedMod;
    }

    public float getPercentBlockMod()
    {
        return this.PercentBlockMod;
    }

    public float getPercentCritChanceMod()
    {
        return this.PercentCritChanceMod;
    }

    public float getPercentCritDamageMod()
    {
        return this.PercentCritDamageMod;
    }

    public float getPercentDodgeMod()
    {
        return this.PercentDodgeMod;
    }

    public float getPercentEXPBonus()
    {
        return this.PercentEXPBonus;
    }

    public float getPercentHPPoolMod()
    {
        return this.PercentHPPoolMod;
    }

    public float getPercentHPRegenMod()
    {
        return this.PercentHPRegenMod;
    }

    public float getPercentLifeStealMod()
    {
        return this.PercentLifeStealMod;
    }

    public float getPercentMagicDamageMod()
    {
        return this.PercentMagicDamageMod;
    }

    public float getPercentMovementSpeedMod()
    {
        return this.PercentMovementSpeedMod;
    }

    public float getPercentMPPoolMod()
    {
        return this.PercentMPPoolMod;
    }

    public float getPercentMPRegenMod()
    {
        return this.PercentMPRegenMod;
    }

    public float getPercentPhysicalDamageMod()
    {
        return this.PercentPhysicalDamageMod;
    }

    public float getPercentSpellBlockMod()
    {
        return this.PercentSpellBlockMod;
    }

    public float getPercentSpellVampMod()
    {
        return this.PercentSpellVampMod;
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
        
        InventoryDataStats that = (InventoryDataStats) o;
        
        if (Float.compare(that.FlatArmorMod, FlatArmorMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatAttackSpeedMod, FlatAttackSpeedMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatBlockMod, FlatBlockMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatCritChanceMod, FlatCritChanceMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatCritDamageMod, FlatCritDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatEXPBonus, FlatEXPBonus) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatEnergyPoolMod, FlatEnergyPoolMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatEnergyRegenMod, FlatEnergyRegenMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatHPPoolMod, FlatHPPoolMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatHPRegenMod, FlatHPRegenMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatMPPoolMod, FlatMPPoolMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatMPRegenMod, FlatMPRegenMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatMagicDamageMod, FlatMagicDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatMovementSpeedMod, FlatMovementSpeedMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatPhysicalDamageMod, FlatPhysicalDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.FlatSpellBlockMod, FlatSpellBlockMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentArmorMod, PercentArmorMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentAttackSpeedMod, PercentAttackSpeedMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentBlockMod, PercentBlockMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentCritChanceMod, PercentCritChanceMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentCritDamageMod, PercentCritDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentDodgeMod, PercentDodgeMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentEXPBonus, PercentEXPBonus) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentHPPoolMod, PercentHPPoolMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentHPRegenMod, PercentHPRegenMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentLifeStealMod, PercentLifeStealMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentMPPoolMod, PercentMPPoolMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentMPRegenMod, PercentMPRegenMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentMagicDamageMod, PercentMagicDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentMovementSpeedMod, PercentMovementSpeedMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentPhysicalDamageMod, PercentPhysicalDamageMod) != 0)
        {
            return false;
        }
        if (Float.compare(that.PercentSpellBlockMod, PercentSpellBlockMod) != 0)
        {
            return false;
        }
        return Float.compare(that.PercentSpellVampMod, PercentSpellVampMod) == 0;
    }
    
    @Override
    public int hashCode()
    {
        int result = FlatArmorMod != +0.0f ? Float.floatToIntBits(FlatArmorMod) : 0;
        result = 31 * result + (FlatAttackSpeedMod != +0.0f ? Float.floatToIntBits(FlatAttackSpeedMod) : 0);
        result = 31 * result + (FlatBlockMod != +0.0f ? Float.floatToIntBits(FlatBlockMod) : 0);
        result = 31 * result + (FlatCritChanceMod != +0.0f ? Float.floatToIntBits(FlatCritChanceMod) : 0);
        result = 31 * result + (FlatCritDamageMod != +0.0f ? Float.floatToIntBits(FlatCritDamageMod) : 0);
        result = 31 * result + (FlatEXPBonus != +0.0f ? Float.floatToIntBits(FlatEXPBonus) : 0);
        result = 31 * result + (FlatEnergyPoolMod != +0.0f ? Float.floatToIntBits(FlatEnergyPoolMod) : 0);
        result = 31 * result + (FlatEnergyRegenMod != +0.0f ? Float.floatToIntBits(FlatEnergyRegenMod) : 0);
        result = 31 * result + (FlatHPPoolMod != +0.0f ? Float.floatToIntBits(FlatHPPoolMod) : 0);
        result = 31 * result + (FlatHPRegenMod != +0.0f ? Float.floatToIntBits(FlatHPRegenMod) : 0);
        result = 31 * result + (FlatMPPoolMod != +0.0f ? Float.floatToIntBits(FlatMPPoolMod) : 0);
        result = 31 * result + (FlatMPRegenMod != +0.0f ? Float.floatToIntBits(FlatMPRegenMod) : 0);
        result = 31 * result + (FlatMagicDamageMod != +0.0f ? Float.floatToIntBits(FlatMagicDamageMod) : 0);
        result = 31 * result + (FlatMovementSpeedMod != +0.0f ? Float.floatToIntBits(FlatMovementSpeedMod) : 0);
        result = 31 * result + (FlatPhysicalDamageMod != +0.0f ? Float.floatToIntBits(FlatPhysicalDamageMod) : 0);
        result = 31 * result + (FlatSpellBlockMod != +0.0f ? Float.floatToIntBits(FlatSpellBlockMod) : 0);
        result = 31 * result + (PercentArmorMod != +0.0f ? Float.floatToIntBits(PercentArmorMod) : 0);
        result = 31 * result + (PercentAttackSpeedMod != +0.0f ? Float.floatToIntBits(PercentAttackSpeedMod) : 0);
        result = 31 * result + (PercentBlockMod != +0.0f ? Float.floatToIntBits(PercentBlockMod) : 0);
        result = 31 * result + (PercentCritChanceMod != +0.0f ? Float.floatToIntBits(PercentCritChanceMod) : 0);
        result = 31 * result + (PercentCritDamageMod != +0.0f ? Float.floatToIntBits(PercentCritDamageMod) : 0);
        result = 31 * result + (PercentDodgeMod != +0.0f ? Float.floatToIntBits(PercentDodgeMod) : 0);
        result = 31 * result + (PercentEXPBonus != +0.0f ? Float.floatToIntBits(PercentEXPBonus) : 0);
        result = 31 * result + (PercentHPPoolMod != +0.0f ? Float.floatToIntBits(PercentHPPoolMod) : 0);
        result = 31 * result + (PercentHPRegenMod != +0.0f ? Float.floatToIntBits(PercentHPRegenMod) : 0);
        result = 31 * result + (PercentLifeStealMod != +0.0f ? Float.floatToIntBits(PercentLifeStealMod) : 0);
        result = 31 * result + (PercentMPPoolMod != +0.0f ? Float.floatToIntBits(PercentMPPoolMod) : 0);
        result = 31 * result + (PercentMPRegenMod != +0.0f ? Float.floatToIntBits(PercentMPRegenMod) : 0);
        result = 31 * result + (PercentMagicDamageMod != +0.0f ? Float.floatToIntBits(PercentMagicDamageMod) : 0);
        result = 31 * result + (PercentMovementSpeedMod != +0.0f ? Float.floatToIntBits(PercentMovementSpeedMod) : 0);
        result = 31 * result + (PercentPhysicalDamageMod != +0.0f ? Float.floatToIntBits(PercentPhysicalDamageMod) : 0);
        result = 31 * result + (PercentSpellBlockMod != +0.0f ? Float.floatToIntBits(PercentSpellBlockMod) : 0);
        result = 31 * result + (PercentSpellVampMod != +0.0f ? Float.floatToIntBits(PercentSpellVampMod) : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "InventoryDataStats{" +
               "FlatArmorMod=" + FlatArmorMod +
               ", FlatAttackSpeedMod=" + FlatAttackSpeedMod +
               ", FlatBlockMod=" + FlatBlockMod +
               ", FlatCritChanceMod=" + FlatCritChanceMod +
               ", FlatCritDamageMod=" + FlatCritDamageMod +
               ", FlatEXPBonus=" + FlatEXPBonus +
               ", FlatEnergyPoolMod=" + FlatEnergyPoolMod +
               ", FlatEnergyRegenMod=" + FlatEnergyRegenMod +
               ", FlatHPPoolMod=" + FlatHPPoolMod +
               ", FlatHPRegenMod=" + FlatHPRegenMod +
               ", FlatMPPoolMod=" + FlatMPPoolMod +
               ", FlatMPRegenMod=" + FlatMPRegenMod +
               ", FlatMagicDamageMod=" + FlatMagicDamageMod +
               ", FlatMovementSpeedMod=" + FlatMovementSpeedMod +
               ", FlatPhysicalDamageMod=" + FlatPhysicalDamageMod +
               ", FlatSpellBlockMod=" + FlatSpellBlockMod +
               ", PercentArmorMod=" + PercentArmorMod +
               ", PercentAttackSpeedMod=" + PercentAttackSpeedMod +
               ", PercentBlockMod=" + PercentBlockMod +
               ", PercentCritChanceMod=" + PercentCritChanceMod +
               ", PercentCritDamageMod=" + PercentCritDamageMod +
               ", PercentDodgeMod=" + PercentDodgeMod +
               ", PercentEXPBonus=" + PercentEXPBonus +
               ", PercentHPPoolMod=" + PercentHPPoolMod +
               ", PercentHPRegenMod=" + PercentHPRegenMod +
               ", PercentLifeStealMod=" + PercentLifeStealMod +
               ", PercentMPPoolMod=" + PercentMPPoolMod +
               ", PercentMPRegenMod=" + PercentMPRegenMod +
               ", PercentMagicDamageMod=" + PercentMagicDamageMod +
               ", PercentMovementSpeedMod=" + PercentMovementSpeedMod +
               ", PercentPhysicalDamageMod=" + PercentPhysicalDamageMod +
               ", PercentSpellBlockMod=" + PercentSpellBlockMod +
               ", PercentSpellVampMod=" + PercentSpellVampMod +
               '}';
    }
}
