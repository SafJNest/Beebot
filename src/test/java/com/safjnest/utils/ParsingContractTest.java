package com.safjnest.utils;

import static org.junit.Assert.*;

import java.util.List;

import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.FilterCodec;
import com.safjnest.spring.controller.lol.LolApiParameters;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ParsingContractTest {

    private enum Mode { AUTO, manual }

    @Test
    public void integerTextKeepsTrimDefaultsAndRange() {
        assertEquals(12, ParseUtils.parseInt(" 12 "));
        assertEquals(-12, ParseUtils.parseInt("-12"));
        assertEquals(12, ParseUtils.parseInt("+12"));
        assertEquals(Integer.MAX_VALUE, ParseUtils.parseInt("2147483647"));
        assertEquals(0, ParseUtils.parseInt("2147483648"));
        assertEquals(0, ParseUtils.parseInt("12.0"));
        assertEquals(0, ParseUtils.parseInt("abc"));
        assertEquals(0, ParseUtils.parseInt((String) null));
        assertEquals(7, ParseUtils.parseInt("", 7));
        assertEquals(7, ParseUtils.parseInt("\u00a012\u00a0", 7));
    }

    @Test
    public void objectConversionKeepsNumberNarrowingAndRejectsUnrelatedTypes() {
        Object[] values = {12, 12L, 12.9d, " 12 ", null, "abc", "12.9", true, new StringBuilder("12"), Long.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY};
        int[] expected = {12, 12, 12, 12, 0, 0, 0, 0, 0, -1, 0, Integer.MAX_VALUE};

        for (int index = 0; index < values.length; index++) {
            assertEquals(expected[index], ParseUtils.parseInt(values[index]));
        }
    }

    @Test
    public void jsonBuildReadersUseTheSameTolerantConversion() {
        JSONObject values = new JSONObject().put("name", " 12 ").put("numeric", 3.9).put("wrong", true);

        assertEquals(12, BuildUtils.readInt(values, "name"));
        assertEquals(3, BuildUtils.readInt(values, "numeric"));
        assertEquals(0, BuildUtils.readInt(values, "wrong"));
        assertEquals(0, BuildUtils.readInt(values, "missing"));
        assertEquals(List.of(12, 3, 0, 0), BuildUtils.jsonArrayToIntList(new JSONArray().put(" 12 ").put(3.9).put(true).put(JSONObject.NULL)));
    }

    @Test
    public void numericOnlyLongConversionDoesNotAcceptTextOrImposeArenaRules() {
        assertEquals(12L, ParseUtils.longValue(12));
        assertEquals(12L, ParseUtils.longValue(12.9));
        assertEquals(-12L, ParseUtils.longValue(-12));
        assertEquals(0L, ParseUtils.longValue("12"));
        assertEquals(0L, ParseUtils.longValue(null));
        assertEquals(0L, ParseUtils.longValue(Double.NaN));
    }

    @Test
    public void nullableValuesRemainDistinctFromZeroAndPreserveNonFiniteDoubles() {
        assertEquals(Integer.valueOf(0), ParseUtils.parseIntOrNull("0"));
        assertEquals(Integer.valueOf(12), ParseUtils.parseIntOrNull(" 12 "));
        assertNull(ParseUtils.parseIntOrNull("invalid"));
        assertNull(ParseUtils.parseIntOrNull("2147483648"));
        assertNull(ParseUtils.parseIntOrNull(null));
        assertEquals(Double.valueOf(1.25), ParseUtils.parseDoubleOrNull(" 1.25 "));
        assertNull(ParseUtils.parseDoubleOrNull("invalid"));
        assertNull(ParseUtils.parseDoubleOrNull(null));
        assertTrue(Double.isNaN(ParseUtils.parseDoubleOrNull("NaN")));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), ParseUtils.parseDoubleOrNull("1e309"));
    }

    @Test
    public void filterNumbersKeepWildcardsAndLongRange() {
        assertEquals(0, FilterCodec.decodeInt(" * "));
        assertEquals(0L, FilterCodec.decodeLong("*"));
        assertEquals(0, FilterCodec.decodeInt(null));
        assertEquals(0, FilterCodec.decodeInt("abc"));
        assertEquals(12, FilterCodec.decodeInt(" 12 "));
        assertEquals(Long.MAX_VALUE, FilterCodec.decodeLong("9223372036854775807"));
        assertEquals(0L, FilterCodec.decodeLong("9223372036854775808"));
        assertEquals("*", FilterCodec.encodeInt(0));
        assertEquals("*", FilterCodec.encodeLong(0));
    }

    @Test
    public void filterEnumsKeepExactThenUppercaseLookupAndNullableOrdinals() {
        assertEquals(Mode.AUTO, FilterCodec.decodeEnum("auto", Mode.class));
        assertEquals(Mode.manual, FilterCodec.decodeEnum("manual", Mode.class));
        assertNull(FilterCodec.decodeEnum("MANUAL", Mode.class));
        assertNull(FilterCodec.decodeEnum("*", Mode.class));
        assertEquals(Mode.AUTO, FilterCodec.decodeOrdinal(" 0 ", Mode.class));
        assertEquals(Mode.manual, FilterCodec.decodeOrdinal("1", Mode.class));
        assertNull(FilterCodec.decodeOrdinal("*", Mode.class));
        assertNull(FilterCodec.decodeOrdinal("-1", Mode.class));
        assertNull(FilterCodec.decodeOrdinal("2", Mode.class));
        assertNull(FilterCodec.decodeOrdinal("0", null));
    }

    @Test
    public void filterKeysKeepDelimitersEmptyFieldsAndEncoding() {
        String raw = FilterCodec.join("12", "*", "AUTO", "0", "");

        assertEquals("12|*|AUTO|0|", raw);
        assertEquals("MTJ8KnxBVVRPfDB8", FilterCodec.encode(raw));
        assertEquals(raw, FilterCodec.decode("MTJ8KnxBVVRPfDB8"));
        assertArrayEquals(new String[] {"12", "*", "AUTO", "0", ""}, FilterCodec.split(raw));
    }

    @Test
    public void apiPageKeepsStrictWhitespaceAndSeparateErrorMessages() {
        assertEquals(12, LolApiParameters.page("12"));
        assertEquals(12, LolApiParameters.page("+12"));

        for (String input : new String[] {null, "", " 12 ", "12.0", "abc", "2147483648"}) {
            ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> LolApiParameters.page(input));
            assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
            assertEquals("Invalid page: must be an integer", failure.getReason());
        }

        for (String input : new String[] {"0", "-1", "-2147483648"})
            assertEquals("Invalid page: must be greater than 0", assertThrows(ResponseStatusException.class,
                () -> LolApiParameters.page(input)).getReason());
    }

    @Test
    public void apiEnumsKeepValidationAtTheHttpBoundary() {
        assertEquals("GOLD", LolApiParameters.rank(" gold ").name());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> LolApiParameters.rank("unknown-rank"));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertTrue(failure.getReason().startsWith("Invalid rank: must be one of:"));
    }
}
