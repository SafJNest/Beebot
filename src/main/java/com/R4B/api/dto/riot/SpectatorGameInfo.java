// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.time.*;
import java.util.*;

import com.R4B.lol.*;

public class SpectatorGameInfo implements java.io.Serializable
{
    private static final long serialVersionUID = -5466430258379033702L;
    
    private List<BannedChampion>       bannedChampions;
    private long                       gameId;
    private long                       gameLength;
    private GameModeType               gameMode;
    private GameQueueType              gameQueueConfigId;
    private long                       gameStartTime;
    private GameType                   gameType;
    private MapType                    mapId;
    private Observer                   observers;
    private List<SpectatorParticipant> participants;
    private LeagueShard                platformId;

    public List<BannedChampion> getBannedChampions()
    {
        return this.bannedChampions == null ? Collections.emptyList() : Collections.unmodifiableList(this.bannedChampions);
    }

    public long getGameId()
    {
        return this.gameId;
    }

    public long getGameLength()
    {
        return this.gameLength;
    }

    public GameModeType getGameMode()
    {
        return this.gameMode;
    }

    public GameQueueType getGameQueueConfig()
    {
        return this.gameQueueConfigId;
    }

    public long getGameStart()
    {
        return this.gameStartTime;
    }

    public ZonedDateTime getGameStartAsDate()
    {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(this.gameStartTime), ZoneOffset.UTC);
    }

    public GameType getGameType()
    {
        return this.gameType;
    }

    public MapType getMap()
    {
        return this.mapId;
    }

    public Observer getObservers()
    {
        return this.observers;
    }

    public LeagueShard getPlatform()
    {
        return this.platformId;
    }

    public List<SpectatorParticipant> getParticipants()
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
        
        SpectatorGameInfo that = (SpectatorGameInfo) o;
        
        if (gameId != that.gameId)
        {
            return false;
        }
        if (gameLength != that.gameLength)
        {
            return false;
        }
        if (gameStartTime != that.gameStartTime)
        {
            return false;
        }
        if (!Objects.equals(bannedChampions, that.bannedChampions))
        {
            return false;
        }
        if (gameMode != that.gameMode)
        {
            return false;
        }
        if (gameQueueConfigId != that.gameQueueConfigId)
        {
            return false;
        }
        if (gameType != that.gameType)
        {
            return false;
        }
        if (mapId != that.mapId)
        {
            return false;
        }
        if (!Objects.equals(observers, that.observers))
        {
            return false;
        }
        if (!Objects.equals(participants, that.participants))
        {
            return false;
        }
        return platformId == that.platformId;
    }
    
    @Override
    public int hashCode()
    {
        int result = bannedChampions != null ? bannedChampions.hashCode() : 0;
        result = 31 * result + Long.hashCode(gameId);
        result = 31 * result + Long.hashCode(gameLength);
        result = 31 * result + (gameMode != null ? gameMode.hashCode() : 0);
        result = 31 * result + (gameQueueConfigId != null ? gameQueueConfigId.hashCode() : 0);
        result = 31 * result + Long.hashCode(gameStartTime);
        result = 31 * result + (gameType != null ? gameType.hashCode() : 0);
        result = 31 * result + (mapId != null ? mapId.hashCode() : 0);
        result = 31 * result + (observers != null ? observers.hashCode() : 0);
        result = 31 * result + (participants != null ? participants.hashCode() : 0);
        result = 31 * result + (platformId != null ? platformId.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString()
    {
        return "SpectatorGameInfo{" +
               "bannedChampions=" + bannedChampions +
               ", gameId=" + gameId +
               ", gameLength=" + gameLength +
               ", gameMode=" + gameMode +
               ", gameQueueConfigId=" + gameQueueConfigId +
               ", gameStartTime=" + gameStartTime +
               ", gameType=" + gameType +
               ", mapId=" + mapId +
               ", observers=" + observers +
               ", participants=" + participants +
               ", platformId=" + platformId +
               '}';
    }
}
