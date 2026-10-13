package com.safjnest.spring.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.R4B.api.dto.riot.Item;
import com.R4B.api.dto.riot.StaticChampion;
import com.R4B.api.dto.riot.StaticSummonerSpell;
import com.safjnest.lol.service.StaticDataService;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.HttpUtils;

@RestController
@RequestMapping("/api/lol/static")
public class StaticDataController {

    @GetMapping("/champions")
    public Map<Integer, StaticChampion> champions() {
        return StaticDataService.getChampions();
    }

    @GetMapping("/champion/{id}")
    public StaticChampion champion(@PathVariable("id") int id) {
        StaticChampion champion = StaticDataService.getChampion(id);
        if (champion == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Champion not found");
        return champion;
    }

    @GetMapping("/items")
    public Map<Integer, Item> items() {
        return StaticDataService.getItems();
    }

    @GetMapping("/item/{id}")
    public Item item(@PathVariable("id") int id) {
        Item item = StaticDataService.getItem(id);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        return item;
    }

    @GetMapping("/summoner-spell/{id}")
    public StaticSummonerSpell summonerSpell(@PathVariable("id") int id) {
        StaticSummonerSpell spell = StaticDataService.getSummonerSpell(id);
        if (spell == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Summoner spell not found");
        return spell;
    }

    @GetMapping("/runes")
    public String runes() {
        String patch = PatchUtils.getPatch();
        String url = "https://ddragon.leagueoflegends.com/cdn/" + patch + ".1/data/en_US/runesReforged.json";
        return StaticDataService.getText(RedisKey.DDRAGON_RUNES, patch, () -> {
            try {
                return HttpUtils.readUrl(url);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }
}
