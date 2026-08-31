package com.safjnest.spring.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.safjnest.nosql.MongoDB;
import com.safjnest.utils.JsonCodec;

@RestController
@RequestMapping("/api/lol/ai")
public class AiTrainingController {

    @GetMapping(value = "/training", produces = MediaType.APPLICATION_JSON_VALUE)
    public StreamingResponseBody training() {
        return output -> {
            try (JsonGenerator json = new JsonFactory().createGenerator(output)) {
                json.writeStartObject();
                json.writeStringField("source", "mongo");
                json.writeArrayFieldStart("samples");
                json.flush();
                MongoDB.forEachAiTrainingSample(sample -> write(json, sample));
                json.writeEndArray();
                json.writeEndObject();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    private static void write(JsonGenerator json, Map<String, Object> sample) {
        try {
            json.writeRawValue(JsonCodec.toJson(sample));
            json.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
