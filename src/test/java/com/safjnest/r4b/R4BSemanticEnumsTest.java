package com.safjnest.r4b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.Test;

public class R4BSemanticEnumsTest {
    private static final List<String> FAMILIES = List.of(
        "api.regions.LeagueShard", "api.regions.RegionShard", "types.lol.TierType",
        "types.lol.TierDivisionType", "types.lol.GameQueueType", "types.lol.LaneType",
        "types.lol.RoleType", "types.lol.TeamType", "types.lol.GameModeType",
        "types.lol.GameType", "types.lol.MapType", "types.lol.MatchlistMatchType",
        "types.KillType", "types.lol.SummonerSpellType", "types.lol.TimelineDamageType",
        "types.lol.BuildingType", "types.lol.TowerType", "types.lol.EventType",
        "types.lol.WardType", "types.lol.MonsterType",
        "types.lol.MonsterSubType", "types.lol.LevelUpType", "types.lol.SpellSlotType",
        "types.TransformType"
    );
    private static final List<String> DATA_METHODS = List.of(
        "getCode", "getCodes", "getValue", "getValues", "getId", "getKeys", "getApiName",
        "getRealmValue", "getTier", "getDivision", "commonName", "toRegionShard",
        "toAccountRegionShard", "opposite", "getDefaultPlatforms", "getSpectatorPlatforms",
        "getGarenaPlatforms", "getValidPlatforms", "getBounds"
    );

    @Test
    public void preservesEverySdkValueAndHistoricalOrdinal() throws Exception {
        for (String family : FAMILIES) {
            Class<?> sdk = sdk(family);
            Class<?> shared = shared(family);
            assertEquals(family, normalize(sdk.getEnumConstants()), normalize(shared.getEnumConstants()));
            for (Object original : sdk.getEnumConstants()) {
                Object extracted = constant(shared, ((Enum<?>) original).name());
                assertEquals(family, ((Enum<?>) original).ordinal(), ((Enum<?>) extracted).ordinal());
            }
        }
    }

    @Test
    public void preservesIdsAliasesAndRoutingData() throws Exception {
        for (String family : FAMILIES) {
            Class<?> sdk = sdk(family);
            Class<?> shared = shared(family);
            for (String getter : DATA_METHODS) {
                Method originalGetter = method(sdk, getter);
                if (originalGetter == null || originalGetter.getDeclaringClass() != sdk) continue;
                Method extractedGetter = shared.getMethod(getter);
                for (Object original : sdk.getEnumConstants()) {
                    Object extracted = constant(shared, ((Enum<?>) original).name());
                    assertEquals(family + "." + ((Enum<?>) original).name() + "." + getter,
                        normalize(originalGetter.invoke(original)), normalize(extractedGetter.invoke(extracted)));
                }
            }
        }
    }

    @Test
    public void resolvesAllSdkCodesIncludingHistoricalAndEmptyLaneAliases() throws Exception {
        for (String family : FAMILIES) {
            Class<?> sdk = sdk(family);
            Class<?> shared = shared(family);
            Method originalLookup = method(sdk, "getFromCode", String.class);
            if (originalLookup == null) continue;
            Method extractedLookup = shared.getMethod("getFromCode", String.class);
            Object sdkReceiver = sdk.getEnumConstants()[0];
            Object sharedReceiver = shared.getEnumConstants()[0];
            for (Object original : sdk.getEnumConstants()) {
                List<String> codes = new ArrayList<>();
                codes.add(((Enum<?>) original).name());
                for (String getter : List.of("getCode", "getCodes", "getValue", "getValues", "getKeys", "getApiName")) {
                    Method data = method(sdk, getter);
                    if (data != null) addCodes(codes, data.invoke(original));
                }
                for (Field field : sdk.getDeclaredFields()) {
                    Class<?> component = field.getType().getComponentType();
                    if (component != String.class && component != Integer.class && component != int.class) continue;
                    field.setAccessible(true);
                    addCodes(codes, field.get(original));
                }
                for (String code : codes) {
                    for (String candidate : List.of(code, code.toLowerCase(Locale.ROOT))) {
                        Object expected;
                        try { expected = normalize(originalLookup.invoke(sdkReceiver, candidate)); }
                        catch (InvocationTargetException exception) {
                            if (exception.getCause() instanceof NumberFormatException) continue;
                            throw exception;
                        }
                        assertEquals(family + " lookup " + candidate,
                            expected,
                            normalize(extractedLookup.invoke(sharedReceiver, candidate)));
                    }
                }
            }
        }
    }

    @Test
    public void keepsAllQueueIdAliasesAndTheirFirstMatchResolution() throws Exception {
        Class<?> sdk = sdk("types.lol.GameQueueType");
        Class<?> shared = shared("types.lol.GameQueueType");
        Method originalLookup = sdk.getMethod("getFromId", int.class);
        Method extractedLookup = shared.getMethod("getFromId", int.class);
        for (Object queue : sdk.getEnumConstants()) {
            Object ids = sdk.getMethod("getValues").invoke(queue);
            for (int index = 0; index < Array.getLength(ids); index++) {
                int id = ((Number) Array.get(ids, index)).intValue();
                assertEquals("queueId=" + id, normalize(originalLookup.invoke(null, id)),
                    normalize(extractedLookup.invoke(null, id)));
            }
        }
        assertEquals(normalize(originalLookup.invoke(null, Integer.MAX_VALUE)),
            normalize(extractedLookup.invoke(null, Integer.MAX_VALUE)));
    }

    @Test
    public void keepsLaneAndRoleSeparateFamilies() throws Exception {
        Class<?> lanes = shared("types.lol.LaneType");
        Class<?> roles = shared("types.lol.RoleType");
        assertNotNull(constant(lanes, "UTILITY"));
        assertNotNull(constant(roles, "DUO_SUPPORT"));
        assertNotNull(constant(roles, "SOLO"));
        assertEquals("NONE", normalize(lanes.getMethod("getFromCode", String.class)
            .invoke(lanes.getEnumConstants()[0], "")));
    }

    @Test
    public void rejectsOutOfRangeHistoricalOrdinalsWithoutWrapping() throws Exception {
        for (String family : FAMILIES) {
            Class<?> type = shared(family);
            Method lookup = type.getMethod("getFromOrdinal", int.class);
            assertNull(family, lookup.invoke(null, -1));
            assertNull(family, lookup.invoke(null, type.getEnumConstants().length));
            assertNull(family, lookup.invoke(null, Integer.MAX_VALUE));
            for (Object value : type.getEnumConstants()) {
                assertEquals(value, lookup.invoke(null, ((Enum<?>) value).ordinal()));
            }
        }
    }

    private static Class<?> sdk(String family) throws ClassNotFoundException {
        return Class.forName("no.stelar7.api.r4j.basic.constants." + family);
    }

    private static Class<?> shared(String family) throws ClassNotFoundException {
        return Class.forName("com.R4B.lol." + family.substring(family.lastIndexOf('.') + 1));
    }

    private static Object constant(Class<?> type, String name) throws ReflectiveOperationException {
        return type.getField(name).get(null);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try { return type.getMethod(name, parameters); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    private static void addCodes(List<String> codes, Object value) {
        if (value == null) return;
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) addCodes(codes, Array.get(value, index));
            return;
        }
        codes.add(value.toString());
    }

    private static Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) return normalize(optional.orElse(null));
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value.getClass().getSimpleName().equals("Rectangle")) {
            List<Object> dimensions = new ArrayList<>();
            try {
                for (String getter : List.of("getX", "getY", "getW", "getH"))
                    dimensions.add(value.getClass().getMethod(getter).invoke(value));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Incomplete map bounds", exception);
            }
            return dimensions;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object item : iterable) values.add(normalize(item));
            return values;
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) values.add(normalize(Array.get(value, index)));
            return values;
        }
        return value;
    }
}
