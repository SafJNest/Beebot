package com.safjnest.core.cache;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Test serialization functionality independently of Redis
 */
public class SerializationUtilsTest {
    
    @Test
    public void testStringSerializationDeserialization() {
        String original = "Hello, Redis Cache!";
        
        String serialized = SerializationUtils.serialize(original);
        assertNotNull("Serialized string should not be null", serialized);
        assertFalse("Serialized string should not be empty", serialized.isEmpty());
        
        String deserialized = SerializationUtils.deserialize(serialized, String.class);
        assertEquals("Deserialized string should match original", original, deserialized);
    }
    
    @Test
    public void testNullSerialization() {
        String serialized = SerializationUtils.serialize(null);
        assertNull("Serialized null should be null", serialized);
        
        String deserialized = SerializationUtils.deserialize(null, String.class);
        assertNull("Deserialized null should be null", deserialized);
    }
    
    @Test
    public void testEmptyStringSerialization() {
        String deserialized = SerializationUtils.deserialize("", String.class);
        assertNull("Deserialized empty string should be null", deserialized);
    }
    
    @Test
    public void testObjectSerialization() {
        TestObject original = new TestObject("test", 42);
        
        String serialized = SerializationUtils.serialize(original);
        assertNotNull("Serialized object should not be null", serialized);
        
        TestObject deserialized = SerializationUtils.deserialize(serialized, TestObject.class);
        assertNotNull("Deserialized object should not be null", deserialized);
        assertEquals("Deserialized name should match", original.name, deserialized.name);
        assertEquals("Deserialized value should match", original.value, deserialized.value);
    }
    
    @Test
    public void testValidSerializedCheck() {
        String validSerialized = SerializationUtils.serialize("test");
        assertTrue("Valid serialized string should be detected", SerializationUtils.isValidSerialized(validSerialized));
        
        assertFalse("Null should not be valid", SerializationUtils.isValidSerialized(null));
        assertFalse("Empty string should not be valid", SerializationUtils.isValidSerialized(""));
        assertFalse("Invalid base64 should not be valid", SerializationUtils.isValidSerialized("not-valid-base64!"));
    }
    
    /**
     * Simple test class for serialization testing
     */
    private static class TestObject implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        int value;
        
        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}