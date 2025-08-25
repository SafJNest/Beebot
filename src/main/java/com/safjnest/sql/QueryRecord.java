package com.safjnest.sql;

import java.sql.Blob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Map;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class QueryRecord extends HashMap<String, String> {
    private static final DateTimeFormatter BASE_FORMATTER =
        new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    private ResultSet resultSet;

    public QueryRecord(ResultSet resultSet){
        super();
        this.resultSet = resultSet;
    }

    public void setResultSet(ResultSet resultSet){
        this.resultSet = resultSet;
    }

    public int getAsInt(String columnName){
        try {
            return Integer.parseInt(get(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    public long getAsLong(String columnName){
        try {
            return Long.parseLong(get(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean getAsBoolean(String columnName){
        return "1".equals(get(columnName)) || "true".equalsIgnoreCase(get(columnName));
    }

    public double getAsDouble(String columnName) {
        try {
            return Double.parseDouble(get(columnName));
        } catch (Exception e) {
            return 0;
        }
    }
    /**
     * 2022-04-08 16:39:57:
     * @param columnName
     * @return
     */
    public long getAsEpochSecond(String columnName){
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(get(columnName), formatter);
            return dateTime.toEpochSecond(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }

    public Timestamp getAsTimestamp(String columnName){
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(get(columnName), formatter);
            return Timestamp.valueOf(dateTime);
        } catch (Exception e) {
            return null;
        }
    }

    public Date getAsDate(String columnName){
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(get(columnName), formatter);
            return Date.valueOf(dateTime.toLocalDate());
        } catch (Exception e) {
            return null;
        }
    }

    public Blob getAsBlob(String columnName){
        try {
            return resultSet.getBlob(columnName);
        } catch (Exception e) {
            return null;
        }
    }


    public boolean emptyValues(){
        for(String value : values()){
            if(value != null && !value.isEmpty())
                return false;
        }
        return true;
    }

    public String[] toArray() {
        String[] array = new String[size()];
        int i = 0;
        for(String col : values()) {
            array[i] = col;
            i++;
        }
        return array;
    }

    public Map<String, String> getAsMap() {
        return new HashMap<>(this);
    
    }


    public LocalDateTime getAsLocalDateTime(String dateTimeStr) {
        String cleaned = dateTimeStr.trim();

        if (cleaned.contains(".")) {
            String[] parts = cleaned.split("\\.", 2);
            String base = parts[0];
            String fraction = parts[1].replaceAll("\\D.*", "");

            if (fraction.length() > 9) {
                fraction = fraction.substring(0, 9);
            }

            cleaned = fraction.isEmpty() ? base : base + "." + fraction;
        }

        return LocalDateTime.parse(cleaned, BASE_FORMATTER);
    }

    public LaneType getAsLaneType(String columnName) {
        try {
            return LaneType.values()[getAsInt(columnName)];
        } catch (Exception e) {
            return null;
        }
    }

    public GameQueueType getAsGameQueueType(String columnName) {
        try {
            return GameQueueType.values()[getAsInt(columnName)];
        } catch (Exception e) {
            return null;
        }
    }

    public TeamType getAsTeamType(String columnName) {
        try {
            return TeamType.values()[getAsInt(columnName)];
        } catch (Exception e) {
            return null;
        }
    }
}
