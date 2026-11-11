package com.safjnest.sound;
import java.util.LinkedHashMap;
import java.util.Map;
import com.safjnest.auth.AuthException;
import org.springframework.http.HttpStatus;

public final class SoundValues {

    private SoundValues() {
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) result.put((String) pairs[i], pairs[i+1]);
        return result;
    }

    public static String id(String value) {
        if (value == null || !value.matches("[0-9]{1,19}") || value.matches("0+")) throw bad("Invalid ID");
        return value;
    }

    public static AuthException bad(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }
}
