package com.safjnest.model.util;

import java.awt.Color;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ColorDeserializer extends JsonDeserializer<Color> {
    @Override
    public Color deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        String hexColor = parser.getText();
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        return new Color(Integer.parseInt(hexColor, 16));
    }
}
