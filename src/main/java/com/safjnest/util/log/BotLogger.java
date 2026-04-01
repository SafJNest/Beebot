package com.safjnest.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.safjnest.core.Bot;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

public class BotLogger {

    private static Logger logger;
    public BotLogger(String name, String resourceBundleName) {
        logger = LoggerFactory.getLogger(BotLogger.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure("rsc/logback.xml");
        } catch (JoranException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cyan
     */
    public static void info (String message, LoggerIDpair... values) {
        logger.info(formatLog(message, values));
        sendLog("INFO", formatLog(message, values));
    }


    /**
     * Yellow
     */
    public static void warning (String message, LoggerIDpair... values) {
        logger.warn(formatLog(message, values));
        sendLog("WARN", formatLog(message, values));
    }

    /**
     * Red
     */
    public static void error (String message, LoggerIDpair... values) {
        logger.error(formatLog(message, values));
        sendLog("ERROR", formatLog(message, values));
    }   

    /**
     * Magenta
     */
    public static void trace (String message, LoggerIDpair... values) {
        logger.trace(formatLog(message, values));
        sendLog("TRACE", formatLog(message, values));
    }

    /**
     * Green
     */
    public static void debug (String message, LoggerIDpair... values) {
        logger.debug(formatLog(message, values));
        sendLog("DEBUG", formatLog(message, values));
    }

    private static void sendLog(String level, String message) {

        // Palette ANSI Discord-safe
        String color;
        switch (level) {
            case "ERROR": color = "\u001B[0;31m"; break;
            case "WARN":  color = "\u001B[0;33m"; break;
            case "INFO":  color = "\u001B[0;36m"; break;
            case "DEBUG": color = "\u001B[0;32m"; break;
            case "TRACE": color = "\u001B[0;35m"; break;
            default:      color = "\u001B[0;37m"; break;
        }
    
        String formatted = "```ansi\n" + color + "[" + level + "] " + message + "\u001B[0;0m\n```";
    
        String[] channelIds = {
            "1485317225903816826",
            "1487043736163713085"
        };
    
        try {
            if (Bot.getJDA() != null) {
                for (String channelId : channelIds) {
                    Bot.getJDA().getTextChannelById(channelId)
                        .sendMessage(formatted)
                        .queue();
                }
            }
        } catch (Exception ignored) {}
    }


    private static String formatLog(String message, LoggerIDpair... values) {
        String formattedMessage = message;
        for (int i = 0; i < values.length; i++) {
            formattedMessage = formattedMessage.replace("{" + i + "}", values[i].format());
        }
        return formattedMessage;
    }

}

