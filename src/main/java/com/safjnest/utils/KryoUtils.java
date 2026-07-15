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
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import com.safjnest.lol.build.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.SlotOption;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.ChampionStatistics.MatchupKey;
import com.safjnest.lol.model.Filter.RankBehavior;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;

public class KryoUtils {

    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(KryoUtils::createCurrentKryo);

    private static Kryo get() {
        return KRYO.get();
    }

    public static String encode(Object object) {
        Kryo kryo = get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeClassAndObject(output, object);
        output.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public static <T> T decode(String encoded, Class<T> type) {
        try {
            if (encoded == null || encoded.isBlank() || type == null) return null;
            return read(get(), Base64.getDecoder().decode(encoded), type);
        } catch (RuntimeException | LinkageError exception) {
            KRYO.remove();
            return null;
        }
    }

    private static Kryo createCurrentKryo() {
        Kryo kryo = createKryo();
        kryo.register(Build.class);
        kryo.register(SlotOption.class);
        kryo.register(ChampionStatistics.class);
        kryo.register(LaneStat.class);
        kryo.register(MatchupKey.class);
        kryo.register(Matchup.class);
        kryo.register(Filter.class);
        kryo.register(RankBehavior.class);
        kryo.register(LaneType.class);
        kryo.register(TeamType.class);
        kryo.register(TierDivisionType.class);
        kryo.register(GameQueueType.class);
        kryo.register(TierType.class);
        kryo.register(LeagueShard.class);
        kryo.register(RuneSignature.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(LinkedHashMap.class);
        kryo.register(ProfileStatistics.class);
        kryo.register(Stats.class);
        kryo.register(Match.class);
        kryo.register(Participant.class);
        kryo.register(MatchResult.class);
        return kryo;
    }

    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        return kryo;
    }

    private static <T> T read(Kryo kryo, byte[] bytes, Class<T> type) {
        Input input = new Input(new ByteArrayInputStream(bytes));
        try {
            Object value = kryo.readClassAndObject(input);
            return type.isInstance(value) ? type.cast(value) : null;
        } finally {
            input.close();
        }
    }
}
