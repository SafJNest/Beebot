// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class TimelineFrame implements java.io.Serializable
{
    private static final long serialVersionUID = -7057434060412917705L;
    
    private List<TimelineFrameEvent>              events;
    private Map<String, TimelineParticipantFrame> participantFrames;
    private long                                  timestamp;
    
    public static long getSerialVersionUID()
    {
        return serialVersionUID;
    }
    
    public List<TimelineFrameEvent> getEvents()
    {
        return events;
    }
    
    public Map<String, TimelineParticipantFrame> getParticipantFrames()
    {
        return participantFrames;
    }
    
    public long getTimestamp()
    {
        return timestamp;
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
        TimelineFrame that = (TimelineFrame) o;
        return timestamp == that.timestamp && Objects.equals(events, that.events) && Objects.equals(participantFrames, that.participantFrames);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(events, participantFrames, timestamp);
    }
    
    @Override
    public String toString()
    {
        return "TimelineFrame{" +
               "events=" + events +
               ", participantFrames=" + participantFrames +
               ", timestamp=" + timestamp +
               '}';
    }
}
