package com.safjnest.sound;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.sound.model.Tag;

import org.junit.Test;

public class TagTest {

    @Test
    public void distinguishesEmptyNewAndStoredTags() {
        Tag empty = new Tag();
        Tag draft = new Tag(0, "meme");
        Tag stored = new Tag(12, "meme");

        assertTrue(empty.isEmpty());
        assertFalse(empty.isNew());
        assertFalse(empty.isStored());
        assertFalse(draft.isEmpty());
        assertTrue(draft.isNew());
        assertFalse(draft.isStored());
        assertFalse(stored.isEmpty());
        assertFalse(stored.isNew());
        assertTrue(stored.isStored());
    }

    @Test
    public void comparesTagsByStoredIdentityAndName() {
        Tag first = new Tag(12, "meme");
        Tag same = new Tag(12, "meme");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new Tag(13, "meme"));
        assertNotEquals(first, new Tag(12, "other"));
        assertNotEquals(first, new Tag(0, "meme"));
        assertNotEquals(first, null);
        assertNotEquals(first, "meme");
    }

    @Test
    public void jsonKeepsOnlyStringIdAndName() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stored = mapper.valueToTree(new Tag(12, "meme"));
        JsonNode draft = mapper.valueToTree(new Tag(0, "new"));
        List<String> fields = new ArrayList<>();
        stored.fieldNames().forEachRemaining(fields::add);

        assertEquals(List.of("name", "id"), fields);
        assertTrue(stored.get("id").isTextual());
        assertEquals("12", stored.get("id").asText());
        assertEquals("meme", stored.get("name").asText());
        assertEquals("0", draft.get("id").asText());
        assertFalse(stored.has("stored"));
        assertFalse(stored.has("new"));
        assertFalse(stored.has("empty"));
    }

    @Test
    public void nameCannotBeNull() {
        assertThrows(NullPointerException.class, () -> new Tag(0, null));
    }
}
