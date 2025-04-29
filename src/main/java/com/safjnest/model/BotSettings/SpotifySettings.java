package com.safjnest.model.BotSettings;

public class SpotifySettings {
    private String clientId;
    private String clientSecret;
    private String spdc;
    private String countryCode;

    public String getClientId() {
        return clientId;
    }
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    public String getClientSecret() {
        return clientSecret;
    }
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    public String getSpdc() {
        return spdc;
    }
    public void setSpdc(String spdc) {
        this.spdc = spdc;
    }
    public String getCountryCode() {
        return countryCode;
    }
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}
