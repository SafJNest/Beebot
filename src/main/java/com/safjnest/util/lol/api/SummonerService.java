package com.safjnest.util.lol.api;

import com.safjnest.util.lol.api.spring.SummonerDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SummonerService {
    
    @Autowired
    private SummonerRepository summonerRepository;
    
    
    @Transactional(readOnly = true)
    public Optional<SummonerDTO> findSummonerByPuuid(String puuid) {
        return summonerRepository.findByPuuid(puuid);
    }

}