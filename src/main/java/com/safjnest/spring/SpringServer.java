package com.safjnest.spring;

import java.io.File;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.springframework.http.server.ServletServerHttpRequest;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.config.Settings;
import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.config.LolApiConfig;

import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class SpringServer {

    private static final String CONTEXT_PATH = "";
    private static final String SERVLET_NAME = "dispatcher";
    private static final String SERVLET_MAPPING = "/";
    private static final int LOAD_ON_STARTUP = 1;
    private static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
    private static final long MULTIPART_OVERHEAD_BYTES = 64L * 1024;

    private final Tomcat tomcat;

    private SpringServer(Tomcat tomcat) {
        this.tomcat = tomcat;
    }

    public static SpringServer start(Settings.Api api) throws Exception {
        Settings.Discord discord = api.discord();
        DiscordAuthService auth = discord == null ? new DiscordAuthService()
            : new DiscordAuthService(discord.clientId(), discord.clientSecret(), discord.encryptionKey());
        SoundCache.getInstance().setCdnUrl(api.cdnUrl());

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(api.port());
        tomcat.getConnector();

        Context context = tomcat.addContext(CONTEXT_PATH, new File(".").getAbsolutePath());
        FilterDef filter = new FilterDef();
        filter.setFilterName("beebotAuth");
        filter.setFilter(new AuthFilter(auth::authenticate));
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
        registerDispatcher(context, auth);

        tomcat.start();
        return new SpringServer(tomcat);
    }

    public void stop() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private static void registerDispatcher(Context context, DiscordAuthService auth) {
        AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("discordAuthService", auth));
        applicationContext.register(LolApiConfig.class);

        DispatcherServlet dispatcherServlet = new ApiDispatcherServlet(applicationContext);
        Wrapper servlet = Tomcat.addServlet(context, SERVLET_NAME, dispatcherServlet);
        servlet.setLoadOnStartup(LOAD_ON_STARTUP);
        servlet.setAsyncSupported(true);
        servlet.setMultipartConfigElement(new MultipartConfigElement("", MAX_UPLOAD_BYTES,
            MAX_UPLOAD_BYTES + MULTIPART_OVERHEAD_BYTES, 0));

        context.addServletMappingDecoded(SERVLET_MAPPING, SERVLET_NAME);
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
