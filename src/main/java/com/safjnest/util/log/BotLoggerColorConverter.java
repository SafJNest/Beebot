package com.safjnest.util.log;

import ch.qos.logback.classic.pattern.LevelConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class BotLoggerColorConverter extends LevelConverter {

    @Override
    public String convert(ILoggingEvent event) {
        switch (event.getLevel().toString()) {
            case "ERROR":
                return "\u001B[31m[" + super.convert(event) + "]\u001B[0m"; // Red
            case "WARN":
                return "\u001B[33m[" + super.convert(event) + "]\u001B[0m"; // Yellow
            case "INFO":
                return "\u001B[34m[" + super.convert(event) + "]\u001B[0m"; // Blue
            case "DEBUG":
                return "\u001B[32m[" + super.convert(event) + "]\u001B[0m"; // Green
            case "TRACE":
                return "\u001B[35m[" + super.convert(event) + "]\u001B[0m"; // Magenta
            default:
                return super.convert(event);
        }
    }
}