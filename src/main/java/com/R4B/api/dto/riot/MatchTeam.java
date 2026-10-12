// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.api.dto.riot;

import java.io.Serializable;
import java.util.*;

import com.R4B.lol.*;

public class MatchTeam implements java.io.Serializable
{
    private static final long serialVersionUID = 2387712867150164023L;
    
    private List<ChampionBan>           bans;
    private Map<String, ObjectiveStats> objectives;
    private TeamType                    teamId;
    private boolean                     win;
    
    public List<ChampionBan> getBans()
    {
        return bans;
    }
    
    public Map<String, ObjectiveStats> getObjectives()
    {
        return objectives;
    }
    
    public TeamType getTeamId()
    {
        return teamId;
    }
    
    public boolean didWin()
    {
        return win;
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
        MatchTeam matchTeam = (MatchTeam) o;
        return win == matchTeam.win && Objects.equals(bans, matchTeam.bans) && Objects.equals(objectives, matchTeam.objectives) && teamId == matchTeam.teamId;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hash(bans, objectives, teamId, win);
    }
    
    @Override
    public String toString()
    {
        return "MatchTeam{" +
               "bans=" + bans +
               ", objectives=" + objectives +
               ", teamId=" + teamId +
               ", win=" + win +
               '}';
    }
}
