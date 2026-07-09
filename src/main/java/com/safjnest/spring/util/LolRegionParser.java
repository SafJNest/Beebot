package com.safjnest.spring.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class LolRegionParser {

    public static LeagueShard parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing region");
        }

        String normalized = value.trim().toUpperCase().replace("-", "").replace("_", "");
        return switch (normalized) {
            case "EUW", "EUW1" -> LeagueShard.EUW1;
            case "EUNE", "EUN", "EUN1" -> LeagueShard.EUN1;
            case "NA", "NA1" -> LeagueShard.NA1;
            case "KR", "KR1" -> LeagueShard.KR;
            case "BR", "BR1" -> LeagueShard.BR1;
            case "JP", "JP1" -> LeagueShard.JP1;
            case "LAN", "LA1" -> LeagueShard.LA1;
            case "LAS", "LA2" -> LeagueShard.LA2;
            case "TR", "TR1" -> LeagueShard.TR1;
            case "RU", "RU1" -> LeagueShard.RU;
            case "OCE", "OC", "OC1" -> LeagueShard.OC1;
            case "VN", "VN2" -> LeagueShard.VN2;
            case "SG", "SG2" -> LeagueShard.SG2;
            case "TW", "TW2" -> LeagueShard.TW2;
            case "ME", "ME1" -> LeagueShard.ME1;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid region");
        };
    }
}
