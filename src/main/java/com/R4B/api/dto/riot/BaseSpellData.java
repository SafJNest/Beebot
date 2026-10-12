// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class BaseSpellData implements java.io.Serializable
{
    private static final long serialVersionUID = 1347663708477953635L;
    
    private List<Double>       cooldown;
    private String             cooldownBurn;
    private List<Integer>      cost;
    private String             costBurn;
    private String             costType;
    private String             description;
    private List<List<Double>> effect;
    private List<String> effectBurn;
    private Image        image;
    private String       key;
    private LevelTip     leveltip;
    private int          maxrank;
    private String             name;
    private Object             range;
    private String             rangeBurn;
    private String             resource;
    private String             tooltip;
    private List<SpellVars>    vars;

    public List<Double> getCooldown()
    {
        return this.cooldown;
    }

    public String getCooldownBurn()
    {
        return this.cooldownBurn;
    }

    public List<Integer> getCost()
    {
        return this.cost;
    }

    public String getCostBurn()
    {
        return this.costBurn;
    }

    public String getCostType()
    {
        return this.costType;
    }

    public String getDescription()
    {
        return this.description;
    }

    public List<List<Double>> getEffect()
    {
        return this.effect;
    }

    public List<String> getEffectBurn()
    {
        return this.effectBurn;
    }

    public Image getImage()
    {
        return this.image;
    }

    public String getKey()
    {
        return this.key;
    }

    public LevelTip getLeveltip()
    {
        return this.leveltip;
    }

    public int getMaxRank()
    {
        return this.maxrank;
    }

    public String getName()
    {
        return this.name;
    }

    public List<Double> getRange()
    {
        return (this.range instanceof List) ? (List<Double>) this.range : Collections.singletonList(Double.parseDouble((String) range));
    }

    public String getRangeBurn()
    {
        return this.rangeBurn;
    }

    public String getResource()
    {
        return this.resource;
    }

    public String getSanitizedDescription()
    {
        return sanitize(description);
    }

    public String getSanitizedTooltip()
    {
        return sanitize(tooltip);
    }
    
    public String getSanitizedTooltip(final int champLevel, final int spellLevel)
    {
        return this.replaceVariables(getSanitizedTooltip(), champLevel, spellLevel);
    }
    
    public String getTooltip(final int champLevel, final int spellLevel)
    {
        return this.replaceVariables(this.tooltip, champLevel, spellLevel);
    }
    
    private String sanitize(String inData)
    {
        String outData = inData;
        outData = outData.replaceAll("<br>", "\n");
        outData = outData.replaceAll("<.+?>", "");
        return outData;
    }
    
    private String replaceVariables(String text, final int clevel, final int slevel)
    {
        if ((clevel > 18) || (clevel < 1))
        {
            throw new IllegalArgumentException("Champion level out of bounds!");
        }
        if ((slevel > this.maxrank) || (slevel < 1))
        {
            throw new IllegalArgumentException("Spell level out of bounds!");
        }
        
        String data = text;
        
        if (this.effect != null)
        {
            int i = 0;
            for (final List<Double> e : this.effect)
            {
                if (e == null)
                {
                    continue;
                }
                
                String regex = String.format("\\{\\{ e%s }}", i);
                String value = e.get(slevel - 1).toString();
                data = data.replaceAll(regex, value);
                i++;
            }
        }
        if (this.vars != null)
        {
            for (final SpellVars var : this.vars)
            {
                if (var == null)
                {
                    continue;
                }
                Double val = var.getCoeff().get(0);
                if (var.getCoeff().size() == this.maxrank)
                {
                    val = var.getCoeff().get(slevel - 1);
                } else if (var.getCoeff().size() == 18)
                {
                    val = var.getCoeff().get(clevel - 1);
                }
                
                String regex = String.format("\\{\\{ %s }}", var.getKey());
                String value = val.toString();
                data = data.replaceAll(regex, value);
            }
        }
        return data;
    }

    public String getTooltip()
    {
        return this.tooltip;
    }

    public List<SpellVars> getVars()
    {
        return this.vars;
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
        BaseSpellData that = (BaseSpellData) o;
        return maxrank == that.maxrank &&
               Objects.equals(cooldown, that.cooldown) &&
               Objects.equals(cooldownBurn, that.cooldownBurn) &&
               Objects.equals(cost, that.cost) &&
               Objects.equals(costBurn, that.costBurn) &&
               Objects.equals(costType, that.costType) &&
               Objects.equals(description, that.description) &&
               Objects.equals(effect, that.effect) &&
               Objects.equals(effectBurn, that.effectBurn) &&
               Objects.equals(image, that.image) &&
               Objects.equals(key, that.key) &&
               Objects.equals(leveltip, that.leveltip) &&
               Objects.equals(name, that.name) &&
               Objects.equals(range, that.range) &&
               Objects.equals(rangeBurn, that.rangeBurn) &&
               Objects.equals(resource, that.resource) &&
               Objects.equals(tooltip, that.tooltip) &&
               Objects.equals(vars, that.vars);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(cooldown, cooldownBurn, cost, costBurn, costType, description, effect, effectBurn, image, key, leveltip, maxrank, name, range, rangeBurn, resource, tooltip, vars);
    }
    
    @Override
    public String toString()
    {
        return "BaseSpellData{" +
               "cooldown=" + cooldown +
               ", cooldownBurn='" + cooldownBurn + '\'' +
               ", cost=" + cost +
               ", costBurn='" + costBurn + '\'' +
               ", costType='" + costType + '\'' +
               ", description='" + description + '\'' +
               ", effect=" + effect +
               ", effectBurn=" + effectBurn +
               ", image=" + image +
               ", key='" + key + '\'' +
               ", leveltip=" + leveltip +
               ", maxrank=" + maxrank +
               ", name='" + name + '\'' +
               ", range=" + range +
               ", rangeBurn='" + rangeBurn + '\'' +
               ", resource='" + resource + '\'' +
               ", tooltip='" + tooltip + '\'' +
               ", vars=" + vars +
               '}';
    }
}
