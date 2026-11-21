package com.safjnest.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoTokenSettings {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String potoken;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String visitordata;
}
