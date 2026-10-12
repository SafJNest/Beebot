// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class RiotAccount implements java.io.Serializable
{
    private static final long serialVersionUID = 5822016483540841587L;
    
    private String puuid;
    private String gameName;
    private String tagLine;
    
    public String getPUUID()
    {
        return puuid;
    }
    
    public String getName()
    {
        return gameName;
    }
    
    public String getTag()
    {
        return tagLine;
    }
    
    @Override
    public String toString()
    {
        return "RiotAccount{" +
               "puuid='" + puuid + '\'' +
               ", gameName='" + gameName + '\'' +
               ", tagLine='" + tagLine + '\'' +
               '}';
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
        RiotAccount that = (RiotAccount) o;
        return Objects.equals(puuid, that.puuid) &&
               Objects.equals(gameName, that.gameName) &&
               Objects.equals(tagLine, that.tagLine);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(puuid, gameName, tagLine);
    }
}
