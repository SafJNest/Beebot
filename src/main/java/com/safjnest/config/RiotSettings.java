package com.safjnest.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiotSettings {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String key;
}
