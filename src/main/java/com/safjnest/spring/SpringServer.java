package com.safjnest.spring;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import com.safjnest.spring.config.LolApiConfig;

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
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector();

        Context context = tomcat.addContext(CONTEXT_PATH, new File(".").getAbsolutePath());
        registerDispatcher(context);

        tomcat.start();
        return new SpringServer(tomcat);
    }

    public void stop() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private static void registerDispatcher(Context context) {
        AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.register(LolApiConfig.class);

        DispatcherServlet dispatcherServlet = new DispatcherServlet(applicationContext);
        Wrapper servlet = Tomcat.addServlet(context, SERVLET_NAME, dispatcherServlet);
        servlet.setLoadOnStartup(LOAD_ON_STARTUP);
        servlet.setAsyncSupported(true);

        context.addServletMappingDecoded(SERVLET_MAPPING, SERVLET_NAME);
    }
}
