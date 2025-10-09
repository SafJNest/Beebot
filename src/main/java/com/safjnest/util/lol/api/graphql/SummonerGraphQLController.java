package com.safjnest.util.lol.api.graphql;

import com.safjnest.util.lol.api.LoLData;
import com.safjnest.util.lol.api.spring.SummonerDTO;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.format.DateTimeFormatter;

@Controller
public class SummonerGraphQLController {
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @QueryMapping
    public SummonerDTO summonerByPuuid(@Argument String puuid) {
        // Usa il tuo helper statico
        return LoLData.findSummonerByPuuid(puuid);
    }
        
    // Mappatura esplicita per campi complessi come lastUpdate
    @SchemaMapping(typeName = "Rank")
    public String lastUpdate(com.safjnest.util.lol.api.spring.RankDTO rank) {
        return rank.getLastUpdate() != null 
            ? rank.getLastUpdate().format(DATE_FORMATTER) 
            : null;
    }
    
    @SchemaMapping(typeName = "Mastery")
    public String lastPlayTime(com.safjnest.util.lol.api.spring.MasteriesDTO mastery) {
        return mastery.getLastPlayTime() != null 
            ? mastery.getLastPlayTime().format(DATE_FORMATTER) 
            : null;
    }
}   