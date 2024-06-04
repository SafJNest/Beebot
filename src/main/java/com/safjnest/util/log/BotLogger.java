package com.safjnest.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotLogger {

    private static Logger logger;
    public BotLogger(String name, String resourceBundleName) {
        logger = LoggerFactory.getLogger(BotLogger.class);

    }

    /**
     * Cyan
     */
    public static void info (String message, LoggerIDpair... values) {
        logger.info(formatLog(message, values));
    }


    /**
     * Yellow
     */
    public static void warning (String message, LoggerIDpair... values) {
        logger.warn(formatLog(message, values));
    }

    /**
     * Red
     */
    public static void error (String message, LoggerIDpair... values) {
        logger.error(formatLog(message, values));
    }

    /**
     * Magenta
     */
    public static void trace (String message, LoggerIDpair... values) {
        logger.trace(formatLog(message, values));
    }

    /**
     * Green
     */
    public static void debug (String message, LoggerIDpair... values) {
        logger.debug(formatLog(message, values));
    }



    private static String formatLog(String message, LoggerIDpair... values) {
        String formattedMessage = message;
        for (int i = 0; i < values.length; i++) {
            formattedMessage = formattedMessage.replace("{" + i + "}", values[i].format());
        }
        return formattedMessage;
    }

}

