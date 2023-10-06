package com.safjnest.Utilities.SQL;

import java.util.Map;
import java.util.Map.Entry;

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
        return "1".equals(row.get(columnName)) || "true".equalsIgnoreCase(row.get(columnName));
    }

    public double getAsDouble(String columnName) {
        try {
            return Double.parseDouble(row.get(columnName));
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    public int size() {
        return row.size();
    }

    public String[] toArray() {
        String[] array = new String[row.size()];
        int i = 0;
        for(String col : row.values()) {
            array[i] = col;
            i++;
        }
        return array;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, String> col : row.entrySet())
            sb.append(col.getKey()).append(": ").append(col.getValue());
        return sb.toString();
    }
}
