package com.safjnest.spring;

import static org.junit.Assert.assertEquals;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.safjnest.auth.DiscordAuthService;
import com.safjnest.spring.controller.DiscordAuthController;

public class SpringPropertiesTest {
    @Test
    public void cdnFilePropertyReachesSoundController() throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader("CDN_URL=https://cdn.example.com\n"));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            SpringServer.applyProperties(context.getEnvironment(), properties);
            context.register(com.safjnest.spring.controller.SoundController.class);
            context.refresh();
            assertEquals("https://cdn.example.com", context.getEnvironment().getProperty("CDN_URL"));
            Field field = com.safjnest.core.cache.managers.SoundCache.class.getDeclaredField("cdnUrl");
            field.setAccessible(true);
            assertEquals("https://cdn.example.com", field.get(com.safjnest.core.cache.managers.SoundCache.getInstance()));
        } finally {
            com.safjnest.core.cache.managers.SoundCache.getInstance().setCdnUrl("");
        }
    }

    @Test
    public void missingCdnFilePropertyPreservesEnvironmentValue() {
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("cdnTestEnvironment",
            java.util.Map.of("CDN_URL", "https://environment.example.com")));
        SpringServer.applyProperties(environment, new Properties());
        assertEquals("https://environment.example.com", environment.getProperty("CDN_URL"));
    }

    @Test
    public void filePropertyReachesAuthController() throws Exception {
        Properties properties = new Properties();
        String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        properties.load(new StringReader("DISCORD_CLIENT_ID=123456789\nDISCORD_CLIENT_SECRET=example-secret\n"
            + "AUTH_ENCRYPTION_KEY=" + encryptionKey + "\nAUTH_ALLOWED_REDIRECT_URIS=https://example.com/callback/\n"));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            SpringServer.applyProperties(context.getEnvironment(), properties);
            context.register(DiscordAuthController.class);
            context.refresh();
            DiscordAuthController controller = context.getBean(DiscordAuthController.class);
            Field serviceField = DiscordAuthController.class.getDeclaredField("auth");
            serviceField.setAccessible(true);
            DiscordAuthService service = (DiscordAuthService) serviceField.get(controller);
            Field clientIdField = DiscordAuthService.class.getDeclaredField("discordClientId");
            clientIdField.setAccessible(true);
            assertEquals("123456789", clientIdField.get(service));
            Field secretField = DiscordAuthService.class.getDeclaredField("discordClientSecret");
            secretField.setAccessible(true);
            assertEquals("example-secret", secretField.get(service));
            Field encryptionField = DiscordAuthService.class.getDeclaredField("encryptionKey");
            encryptionField.setAccessible(true);
            SecretKeySpec key = (SecretKeySpec) encryptionField.get(service);
            assertEquals(encryptionKey, Base64.getEncoder().encodeToString(key.getEncoded()));
            assertEquals("https://example.com/callback/", context.getEnvironment().getProperty("AUTH_ALLOWED_REDIRECT_URIS"));
        }
    }
}
