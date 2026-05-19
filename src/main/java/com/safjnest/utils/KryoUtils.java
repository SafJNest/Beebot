package com.safjnest.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.SlotOption;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.ChampionStatistics.MatchupKey;
import com.safjnest.lol.model.Filter.RankBehavior;

public class KryoUtils {

    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);

        kryo.register(Build.class);
        kryo.register(SlotOption.class);
        kryo.register(ChampionStatistics.class);
        kryo.register(LaneStat.class);
        kryo.register(MatchupKey.class);
        kryo.register(Matchup.class);
        kryo.register(Filter.class);
        kryo.register(RankBehavior.class);
        kryo.register(LaneType.class);
        kryo.register(GameQueueType.class);
        kryo.register(TierType.class);
        kryo.register(LeagueShard.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(LinkedHashMap.class);

        return kryo;
    });

    private static Kryo get() {
        return KRYO.get();
    }

    public static String encode(Object object) {
        Kryo kryo = get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, object);
        output.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public static <T> T decode(String encoded, Class<T> type) {
        Kryo kryo = get();
        byte[] bytes = Base64.getDecoder().decode(encoded);
        Input input = new Input(new ByteArrayInputStream(bytes));
        T obj = kryo.readObject(input, type);
        input.close();
        return obj;
    }
}