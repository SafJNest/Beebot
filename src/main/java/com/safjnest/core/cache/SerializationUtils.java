package com.safjnest.core.cache;

import java.io.*;
import java.util.Base64;

/**
 * Utility class for serializing/deserializing objects to/from Redis
 */
public class SerializationUtils {
    
    /**
     * Serialize an object to a Base64 string
     */
    public static String serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(obj);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }
    
    /**
     * Deserialize an object from a Base64 string
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(String data, Class<T> clazz) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            Object obj = ois.readObject();
            if (clazz.equals(Object.class) || clazz.isInstance(obj)) {
                return (T) obj;
            }
            return null;
            
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }
    
    /**
     * Check if a string is a valid serialized object
     */
    public static boolean isValidSerialized(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        
        try {
            Base64.getDecoder().decode(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}