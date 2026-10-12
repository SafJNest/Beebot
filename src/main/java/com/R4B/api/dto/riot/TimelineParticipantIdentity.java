// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.Objects;

import com.R4B.lol.*;

public class TimelineParticipantIdentity implements java.io.Serializable
{
    private static final long serialVersionUID = -6722375304471308188L;
    
    private int    participantId;
    private String puuid;
    
    public int getParticipantId()
    {
        return participantId;
    }
    
    public String getPuuid()
    {
        return puuid;
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
        TimelineParticipantIdentity that = (TimelineParticipantIdentity) o;
        return participantId == that.participantId && Objects.equals(puuid, that.puuid);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(participantId, puuid);
    }
    
    @Override
    public String toString()
    {
        return "MatchParticipantIdentity{" +
               "participantId=" + participantId +
               ", puuid='" + puuid + '\'' +
               '}';
    }
}
