package com.safjnest.util.lol.api.graphql;

import com.safjnest.util.lol.api.LoLData;
import com.safjnest.util.lol.api.spring.MatchDTO;
import com.safjnest.util.lol.api.spring.ParticipantDTO;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class MatchGraphQLController {
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @QueryMapping
    public MatchDTO matchById(@Argument Integer id) {
        return LoLData.findMatchById(id);
    }
    
    @QueryMapping
    public MatchDTO matchByGameId(@Argument String gameId) {
        return LoLData.findMatchByGameId(gameId);
    }
    
    @QueryMapping
    public List<MatchDTO> matchesBySummoner(@Argument String puuid, @Argument Integer limit) {
        return LoLData.findMatchesBySummonerPuuid(puuid, limit);
    }
    
    @QueryMapping
    public List<MatchDTO> matchesBySummonerAndQueue(@Argument String puuid, 
                                                   @Argument String queue,
                                                   @Argument Integer limit) {
        return LoLData.findMatchesBySummonerPuuidAndQueue(puuid, queue, limit);
    }
    
    @QueryMapping
    public ParticipantDTO participantById(@Argument Integer id) {
        return LoLData.findParticipantById(id);
    }
    
    @QueryMapping
    public List<ParticipantDTO> participantsBySummoner(@Argument String puuid, @Argument Integer limit) {
        return LoLData.findParticipantsBySummonerPuuid(puuid, limit);
    }
        
    // Formatter per date e orari
    @SchemaMapping(typeName = "Match")
    public String timeStart(MatchDTO match) {
        return match.getTimeStart() != null 
            ? match.getTimeStart().format(DATE_FORMATTER) 
            : null;
    }
    
    @SchemaMapping(typeName = "Match")
    public String timeEnd(MatchDTO match) {
        return match.getTimeEnd() != null 
            ? match.getTimeEnd().format(DATE_FORMATTER) 
            : null;
    }
}