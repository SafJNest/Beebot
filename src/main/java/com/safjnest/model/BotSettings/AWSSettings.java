package com.safjnest.model.BotSettings;
import lombok.Data;

@Data
public class AWSSettings {
    private String accessKey;
    private String secretKey;
    private String bucketName;
}
