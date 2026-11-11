package com.safjnest.spring;

import java.io.File;
import java.util.Properties;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.springframework.http.server.ServletServerHttpRequest;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.safjnest.spring.config.LolApiConfig;
import com.safjnest.spring.config.BeebotAuthFilter;
import com.safjnest.auth.DiscordAuthService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class SpringServer {

    private static final String CONTEXT_PATH = "";
    private static final String SERVLET_NAME = "dispatcher";
    private static final String SERVLET_MAPPING = "/";
    private static final int LOAD_ON_STARTUP = 1;

    private final Tomcat tomcat;

    private SpringServer(Tomcat tomcat) {
        this.tomcat = tomcat;
    }

    public static SpringServer start(int port) throws Exception {
        return start(port, new Properties());
    }

    public static SpringServer start(int port, Properties properties) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();

        Context context = tomcat.addContext(CONTEXT_PATH, new File(".").getAbsolutePath());
        FilterDef filter = new FilterDef();
        filter.setFilterName("beebotAuth");
        filter.setFilter(new BeebotAuthFilter(new DiscordAuthService(properties.getProperty("DISCORD_CLIENT_ID"),
            properties.getProperty("DISCORD_CLIENT_SECRET"), properties.getProperty("AUTH_ENCRYPTION_KEY"))::authenticate));
        filter.setAsyncSupported("true");
        context.addFilterDef(filter);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("beebotAuth");
        mapping.addURLPattern("/*");
        mapping.setDispatcher("REQUEST");
        mapping.setDispatcher("ASYNC");
        mapping.setDispatcher("FORWARD");
        mapping.setDispatcher("INCLUDE");
        mapping.setDispatcher("ERROR");
        context.addFilterMap(mapping);
        registerDispatcher(context, properties);

        tomcat.start();
        return new SpringServer(tomcat);
    }

    public void stop() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private static void registerDispatcher(Context context, Properties properties) {
        AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
        applyProperties(applicationContext.getEnvironment(), properties);
        applicationContext.register(LolApiConfig.class);

        DispatcherServlet dispatcherServlet = new ApiDispatcherServlet(applicationContext);
        Wrapper servlet = Tomcat.addServlet(context, SERVLET_NAME, dispatcherServlet);
        servlet.setLoadOnStartup(LOAD_ON_STARTUP);
        servlet.setAsyncSupported(true);

        context.addServletMappingDecoded(SERVLET_MAPPING, SERVLET_NAME);
    }

    static void applyProperties(ConfigurableEnvironment environment, Properties properties) {
        environment.getPropertySources().addFirst(new MapPropertySource("beebotSpringProperties",
            Map.of("DISCORD_CLIENT_ID", properties.getProperty("DISCORD_CLIENT_ID", ""),
                "DISCORD_CLIENT_SECRET", properties.getProperty("DISCORD_CLIENT_SECRET", ""),
                "AUTH_ENCRYPTION_KEY", properties.getProperty("AUTH_ENCRYPTION_KEY", ""),
                "AUTH_ALLOWED_REDIRECT_URIS", properties.getProperty("AUTH_ALLOWED_REDIRECT_URIS", ""),
                "CDN_URL", properties.getProperty("CDN_URL", environment.getProperty("CDN_URL", "")))));
    }

    private static final class ApiDispatcherServlet extends DispatcherServlet {

        private ApiDispatcherServlet(AnnotationConfigWebApplicationContext applicationContext) {
            super(applicationContext);
        }

        @Override
        protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
            throw new NoHandlerFoundException(
                request.getMethod(),
                request.getRequestURI(),
                new ServletServerHttpRequest(request).getHeaders()
            );
        }
    }
}
