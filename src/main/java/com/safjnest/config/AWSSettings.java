package com.safjnest.config;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AWSSettings {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String accessKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secretKey;
    private String bucketName;
}
