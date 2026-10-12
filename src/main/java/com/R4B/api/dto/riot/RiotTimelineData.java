// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class RiotTimelineData implements java.io.Serializable
{
    private static final long serialVersionUID = -4143523364776512003L;
    
    private int                               frameInterval;
    private List<TimelineFrame>               frames;
    private long                              gameId;
    private List<TimelineParticipantIdentity> participants;
    
    public int getFrameInterval()
    {
        return frameInterval;
    }
    
    public List<TimelineFrame> getFrames()
    {
        return frames;
    }
    
    public long getGameId()
    {
        return gameId;
    }
    
    public List<TimelineParticipantIdentity> getParticipants()
    {
        return participants;
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
        RiotTimelineData that = (RiotTimelineData) o;
        return frameInterval == that.frameInterval && gameId == that.gameId && Objects.equals(frames, that.frames) && Objects.equals(participants, that.participants);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(frameInterval, frames, gameId, participants);
    }
    
    @Override
    public String toString()
    {
        return "RiotTimelineData{" +
               "frameInterval=" + frameInterval +
               ", frames=" + frames +
               ", gameId=" + gameId +
               ", participants=" + participants +
               '}';
    }
}
