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
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;

public class KryoUtils {

    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(KryoUtils::createStableKryo);
    private static final ThreadLocal<Kryo> LEGACY_KRYO = ThreadLocal.withInitial(KryoUtils::createCurrentLegacyKryo);
    private static final ThreadLocal<Kryo> LEGACY_PROFILE_KRYO = ThreadLocal.withInitial(KryoUtils::createProfileLegacyKryo);

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
        byte[] bytes = Base64.getDecoder().decode(encoded);
        RuntimeException stableException;
        try {
            return read(get(), bytes, type);
        } catch (RuntimeException exception) {
            stableException = exception;
        }

        try {
            return read(LEGACY_KRYO.get(), bytes, type);
        } catch (RuntimeException legacyException) {
            stableException.addSuppressed(legacyException);
        }

        if (type == ChampionStatistics.class) {
            try {
                return read(LEGACY_PROFILE_KRYO.get(), bytes, type);
            } catch (RuntimeException legacyProfileException) {
                stableException.addSuppressed(legacyProfileException);
            }
        }

        throw stableException;
    }

    private static Kryo createStableKryo() {
        Kryo kryo = createKryo();
        kryo.register(Build.class, 10);
        kryo.register(SlotOption.class, 11);
        kryo.register(ChampionStatistics.class, 12);
        kryo.register(LaneStat.class, 13);
        kryo.register(MatchupKey.class, 14);
        kryo.register(Matchup.class, 15);
        kryo.register(Filter.class, 16);
        kryo.register(RankBehavior.class, 17);
        kryo.register(LaneType.class, 18);
        kryo.register(GameQueueType.class, 19);
        kryo.register(TierType.class, 20);
        kryo.register(LeagueShard.class, 21);
        kryo.register(ArrayList.class, 22);
        kryo.register(HashMap.class, 23);
        kryo.register(LinkedHashMap.class, 24);
        kryo.register(ProfileStatistics.class, 25);
        kryo.register(Stats.class, 26);
        kryo.register(Match.class, 27);
        kryo.register(Participant.class, 28);
        kryo.register(MatchResult.class, 29);
        return kryo;
    }

    private static Kryo createCurrentLegacyKryo() {
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
        kryo.register(GameQueueType.class);
        kryo.register(TierType.class);
        kryo.register(LeagueShard.class);
        kryo.register(ProfileStatistics.class);
        kryo.register(Stats.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(LinkedHashMap.class);
        kryo.register(Match.class);
        kryo.register(Participant.class);
        kryo.register(MatchResult.class);
        return kryo;
    }

    private static Kryo createProfileLegacyKryo() {
        Kryo kryo = createKryo();
        kryo.register(Build.class, 10);
        kryo.register(SlotOption.class, 11);
        kryo.register(ChampionStatistics.class, 12);
        kryo.register(LaneStat.class, 13);
        kryo.register(MatchupKey.class, 14);
        kryo.register(Matchup.class, 15);
        kryo.register(Filter.class, 16);
        kryo.register(RankBehavior.class, 17);
        kryo.register(LaneType.class, 18);
        kryo.register(GameQueueType.class, 19);
        kryo.register(TierType.class, 20);
        kryo.register(LeagueShard.class, 21);
        kryo.register(ProfileStatistics.class, 22);
        kryo.register(Stats.class, 23);
        kryo.register(Object.class, 24);
        kryo.register(ArrayList.class, 25);
        kryo.register(HashMap.class, 26);
        kryo.register(LinkedHashMap.class, 27);
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
            return kryo.readObject(input, type);
        } finally {
            input.close();
        }
    }
}
