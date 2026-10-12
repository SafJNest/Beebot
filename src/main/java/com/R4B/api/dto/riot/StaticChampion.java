// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class StaticChampion implements java.io.Serializable
{
    private static final long serialVersionUID = -5036593383951582883L;
    
    private List<String>              allytips;
    private String                    blurb;
    private List<String>              enemytips;
    private int                       id;
    private Image                     image;
    private Info                      info;
    private String                    key;
    private String                    lore;
    private String                    name;
    private String                    partype;
    private Passive                   passive;
    private List<Recommended>         recommended;
    private List<Skin>                skins;
    private List<StaticChampionSpell> spells;
    private StaticChampionStats       stats;
    private List<String>              tags;
    private String                    title;

    public List<String> getAllytips()
    {
        return this.allytips;
    }

    public String getBlurb()
    {
        return this.blurb;
    }

    public List<String> getEnemytips()
    {
        return this.enemytips;
    }

    public int getId()
    {
        return this.id;
    }

    public Image getImage()
    {
        return this.image;
    }

    public Info getInfo()
    {
        return this.info;
    }

    public String getKey()
    {
        return this.key;
    }

    public String getLore()
    {
        return this.lore;
    }

    public String getName()
    {
        return this.name;
    }

    public String getPartype()
    {
        return this.partype;
    }

    public Passive getPassive()
    {
        return this.passive;
    }

    public List<Recommended> getRecommended()
    {
        return this.recommended;
    }

    public List<Skin> getSkins()
    {
        return this.skins;
    }

    public List<StaticChampionSpell> getSpells()
    {
        return this.spells;
    }

    public StaticChampionStats getStats()
    {
        return this.stats;
    }

    public List<String> getTags()
    {
        return this.tags;
    }

    public String getTitle()
    {
        return this.title;
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
        StaticChampion that = (StaticChampion) o;
        return id == that.id &&
               Objects.equals(allytips, that.allytips) &&
               Objects.equals(blurb, that.blurb) &&
               Objects.equals(enemytips, that.enemytips) &&
               Objects.equals(image, that.image) &&
               Objects.equals(info, that.info) &&
               Objects.equals(key, that.key) &&
               Objects.equals(lore, that.lore) &&
               Objects.equals(name, that.name) &&
               Objects.equals(partype, that.partype) &&
               Objects.equals(passive, that.passive) &&
               Objects.equals(recommended, that.recommended) &&
               Objects.equals(skins, that.skins) &&
               Objects.equals(spells, that.spells) &&
               Objects.equals(stats, that.stats) &&
               Objects.equals(tags, that.tags) &&
               Objects.equals(title, that.title);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(allytips, blurb, enemytips, id, image, info, key, lore, name, partype, passive, recommended, skins, spells, stats, tags, title);
    }
    
    @Override
    public String toString()
    {
        return "StaticChampion{" +
               "allytips=" + allytips +
               ", blurb='" + blurb + '\'' +
               ", enemytips=" + enemytips +
               ", id=" + id +
               ", image=" + image +
               ", info=" + info +
               ", key='" + key + '\'' +
               ", lore='" + lore + '\'' +
               ", name='" + name + '\'' +
               ", partype='" + partype + '\'' +
               ", passive=" + passive +
               ", recommended=" + recommended +
               ", skins=" + skins +
               ", spells=" + spells +
               ", stats=" + stats +
               ", tags=" + tags +
               ", title='" + title + '\'' +
               '}';
    }
}
