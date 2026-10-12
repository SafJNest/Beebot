// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class Item implements java.io.Serializable
{
    private static final long serialVersionUID = 7919515074046086833L;
    
    private Map<String, String>  effect;
    private String               colloq;
    private boolean              consumeOnFull;
    private boolean              consumed;
    private int                  depth;
    private String               description;
    private List<String>         from;
    private Gold                 gold;
    private String               group;
    private boolean              hideFromAll;
    private int                  id;
    private Image                image;
    private boolean              inStore;
    private List<String>         into;
    private Map<String, Boolean> maps;
    private String               name;
    private String               plaintext;
    private String               requiredChampion;
    private MetaData             rune;
    private int                  specialRecipe;
    private int                  stacks;
    private InventoryDataStats   stats;
    private List<String>         tags;

    public Map<String, String> getEffect()
    {
        return effect;
    }

    public String getColloq()
    {
        return colloq;
    }

    public boolean isConsumeOnFull()
    {
        return consumeOnFull;
    }

    public boolean isConsumed()
    {
        return consumed;
    }

    public int getDepth()
    {
        return depth;
    }

    public String getDescription()
    {
        return description;
    }

    public List<String> getFrom()
    {
        return from;
    }

    public Gold getGold()
    {
        return gold;
    }

    public String getGroup()
    {
        return group;
    }

    public boolean isHideFromAll()
    {
        return hideFromAll;
    }

    public int getId()
    {
        return id;
    }

    public Image getImage()
    {
        return image;
    }

    public boolean isInStore()
    {
        return inStore;
    }

    public List<String> getInto()
    {
        return into;
    }

    public Map<String, Boolean> getMaps()
    {
        return maps;
    }

    public String getName()
    {
        return name;
    }

    public String getPlaintext()
    {
        return plaintext;
    }

    public String getRequiredChampion()
    {
        return requiredChampion;
    }

    public MetaData getRune()
    {
        return rune;
    }

    public String getSanitizedDescription()
    {
        return sanitize(this.description);
    }
    
    private String sanitize(String inData)
    {
        String outData = inData;
        outData = outData.replaceAll("<br>", "\n");
        outData = outData.replaceAll("<.+?>", "");
        return outData;
    }

    public int getSpecialRecipe()
    {
        return specialRecipe;
    }

    public int getStacks()
    {
        return stacks;
    }

    public InventoryDataStats getStats()
    {
        return stats;
    }

    public List<String> getTags()
    {
        return tags;
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
        Item item = (Item) o;
        return consumeOnFull == item.consumeOnFull &&
               consumed == item.consumed &&
               depth == item.depth &&
               hideFromAll == item.hideFromAll &&
               id == item.id &&
               inStore == item.inStore &&
               specialRecipe == item.specialRecipe &&
               stacks == item.stacks &&
               Objects.equals(effect, item.effect) &&
               Objects.equals(colloq, item.colloq) &&
               Objects.equals(description, item.description) &&
               Objects.equals(from, item.from) &&
               Objects.equals(gold, item.gold) &&
               Objects.equals(group, item.group) &&
               Objects.equals(image, item.image) &&
               Objects.equals(into, item.into) &&
               Objects.equals(maps, item.maps) &&
               Objects.equals(name, item.name) &&
               Objects.equals(plaintext, item.plaintext) &&
               Objects.equals(requiredChampion, item.requiredChampion) &&
               Objects.equals(rune, item.rune) &&
               Objects.equals(stats, item.stats) &&
               Objects.equals(tags, item.tags);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(effect, colloq, consumeOnFull, consumed, depth, description, from, gold, group, hideFromAll, id, image, inStore, into, maps, name, plaintext, requiredChampion, rune, specialRecipe, stacks, stats, tags);
    }
    
    @Override
    public String toString()
    {
        return "Item{" +
               "effect=" + effect +
               ", colloq='" + colloq + '\'' +
               ", consumeOnFull=" + consumeOnFull +
               ", consumed=" + consumed +
               ", depth=" + depth +
               ", description='" + description + '\'' +
               ", from=" + from +
               ", gold=" + gold +
               ", group='" + group + '\'' +
               ", hideFromAll=" + hideFromAll +
               ", id=" + id +
               ", image=" + image +
               ", inStore=" + inStore +
               ", into=" + into +
               ", maps=" + maps +
               ", name='" + name + '\'' +
               ", plaintext='" + plaintext + '\'' +
               ", requiredChampion='" + requiredChampion + '\'' +
               ", rune=" + rune +
               ", specialRecipe=" + specialRecipe +
               ", stacks=" + stacks +
               ", stats=" + stats +
               ", tags=" + tags +
               '}';
    }
}
