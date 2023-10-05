package com.safjnest.Utilities.SQL;

import java.util.Map;

public class ResultRow {
    Map<String, String> row;

    public ResultRow(Map<String, String> row){
        this.row = row;
    }

    public String get(String columnName){
        try {
            return row.get(columnName);
        } catch (Exception e) {
            return null;
        }
    }

    public int getAsInt(String columnName){
        try {
            return Integer.parseInt(row.get(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    public long getAsLong(String columnName){
        try {
            return Long.parseLong(row.get(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean getAsBoolean(String columnName){
        try {
            return Boolean.parseBoolean(row.get(columnName));
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    public double getAsDouble(String columnName) {
        try {
            return Double.parseDouble(row.get(columnName));
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }
}
