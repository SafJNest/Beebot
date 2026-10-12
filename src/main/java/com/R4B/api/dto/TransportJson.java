package com.R4B.api.dto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
public final class TransportJson {
    private TransportJson() {}
    public static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.addMixIn(ChampionStatistics.class, FilterMixin.class);
        mapper.addMixIn(Build.class, FilterMixin.class);
        mapper.addMixIn(Filter.class, FilterVisibility.class);
        return mapper;
    }
    @JsonIgnoreProperties("filter") private abstract static class FilterMixin {}
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) private abstract static class FilterVisibility {}
}
