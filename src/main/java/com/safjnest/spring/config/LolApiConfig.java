package com.safjnest.spring.config;

import java.util.List;
import java.io.IOException;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.safjnest.spring")
public class LolApiConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule arena = new SimpleModule();
        arena.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription description,
                    JsonSerializer<?> serializer) {
                if (description.getBeanClass() == Build.class || description.getBeanClass() == ChampionStatistics.class)
                    return new ArenaApiSerializer(serializer);
                return serializer;
            }
        });
        mapper.registerModule(arena);
        mapper.addMixIn(ChampionStatistics.class, FilterApiMixin.class);
        mapper.addMixIn(Build.class, FilterApiMixin.class);
        mapper.addMixIn(Filter.class, FilterApiVisibilityMixin.class);
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
    }

    @JsonIgnoreProperties("filter")
    private abstract static class FilterApiMixin {}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private abstract static class FilterApiVisibilityMixin {}

    private static final class ArenaApiSerializer extends JsonSerializer<Object> implements ResolvableSerializer {
        private final JsonSerializer<Object> standard;

        @SuppressWarnings("unchecked")
        private ArenaApiSerializer(JsonSerializer<?> standard) {
            this.standard = (JsonSerializer<Object>) standard;
        }

        @Override
        public void resolve(SerializerProvider provider) throws JsonMappingException {
            if (standard instanceof ResolvableSerializer resolvable) resolvable.resolve(provider);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value instanceof Build build && build.filter() != null
                    && build.filter().queue() == GameQueueType.CHERRY && build.arena() != null) {
                provider.defaultSerializeValue(build.arena(), generator);
                return;
            }
            if (value instanceof ChampionStatistics stats && stats.filter() != null
                    && stats.filter().queue() == GameQueueType.CHERRY) {
                generator.writeStartObject();
                serializeArenaOverview(stats.overview(), generator, provider);
                provider.defaultSerializeField("powerCurve", stats.powerCurve(), generator);
                provider.defaultSerializeField("trend", stats.trend(), generator);
                provider.defaultSerializeField("synergies", stats.synergies(), generator);
                provider.defaultSerializeField("subteams", stats.subteams(), generator);
                provider.defaultSerializeField("subteamsById", stats.subteamsById(), generator);
                generator.writeEndObject();
                return;
            }
            standard.serialize(value, generator, provider);
        }

        private static void serializeArenaOverview(ChampionStatistics.Overview overview,
                JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeObjectFieldStart("overview");
            provider.defaultSerializeField("games", overview.games(), generator);
            provider.defaultSerializeField("picks", overview.picks(), generator);
            provider.defaultSerializeField("bans", overview.bans(), generator);
            provider.defaultSerializeField("wins", overview.wins(), generator);
            provider.defaultSerializeField("winrate", overview.winrate(), generator);
            provider.defaultSerializeField("pickrate", overview.pickrate(), generator);
            provider.defaultSerializeField("banrate", overview.banrate(), generator);
            provider.defaultSerializeField("kda", overview.kda(), generator);
            provider.defaultSerializeField("csPerMinute", overview.csPerMinute(), generator);
            provider.defaultSerializeField("goldPerMinute", overview.goldPerMinute(), generator);
            provider.defaultSerializeField("damageProfile", overview.damageProfile(), generator);
            provider.defaultSerializeField("averagePlacement", overview.averagePlacement(), generator);
            provider.defaultSerializeField("placements", overview.placements(), generator);
            generator.writeEndObject();
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/lol/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*");
        registry.addMapping("/api/status")
            .allowedOrigins("*")
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*");
    }
}
