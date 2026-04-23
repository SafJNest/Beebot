package com.safjnest.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.safjnest.lol.build.ChampionBuild;
import com.safjnest.lol.build.ChampionBuild.SlotOption;

public class KryoUtils {

    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();

        kryo.register(ChampionBuild.class);
        kryo.register(SlotOption.class);
        kryo.register(ArrayList.class);

        kryo.setRegistrationRequired(false);


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