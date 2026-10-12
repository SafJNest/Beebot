// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;

import com.R4B.lol.*;

public class Image implements java.io.Serializable
{
    private static final long serialVersionUID = 4836185346156791918L;
    
    private String full;
    private String group;
    private String sprite;
    private int    h;
    private int    w;
    private int    x;
    private int    y;

    public String getFull()
    {
        return this.full;
    }

    public String getGroup()
    {
        return this.group;
    }

    public int getH()
    {
        return this.h;
    }

    public String getSprite()
    {
        return this.sprite;
    }

    public int getW()
    {
        return this.w;
    }

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
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
        
        Image image = (Image) o;
        
        if (h != image.h)
        {
            return false;
        }
        if (w != image.w)
        {
            return false;
        }
        if (x != image.x)
        {
            return false;
        }
        if (y != image.y)
        {
            return false;
        }
        if ((full != null) ? !full.equals(image.full) : (image.full != null))
        {
            return false;
        }
        if ((group != null) ? !group.equals(image.group) : (image.group != null))
        {
            return false;
        }
        return (sprite != null) ? sprite.equals(image.sprite) : (image.sprite == null);
    }
    
    @Override
    public int hashCode()
    {
        int result = full != null ? full.hashCode() : 0;
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (sprite != null ? sprite.hashCode() : 0);
        result = 31 * result + h;
        result = 31 * result + w;
        result = 31 * result + x;
        result = 31 * result + y;
        return result;
    }
    
    @Override
    public String toString()
    {
        return "Image{" +
               "full='" + full + '\'' +
               ", group='" + group + '\'' +
               ", h=" + h +
               ", sprite='" + sprite + '\'' +
               ", w=" + w +
               ", x=" + x +
               ", y=" + y +
               '}';
    }
}
