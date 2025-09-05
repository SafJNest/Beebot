package com.safjnest.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryResult extends ArrayList<QueryRecord> {

    private boolean success = false;
    private int affectedRows = 0;

    public QueryResult(){
        super();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getAffectedRows() {
        return affectedRows;
    }
    
    public void setAffectedRows(int affectedRows) {
        this.affectedRows = affectedRows;
    }

    public List<Map<String, String>> toList(){
        List<Map<String, String>> list = new ArrayList<>();
        for(QueryRecord row : this){
            list.add(row.getAsMap());
        }
        return list;
    }

    public List<String> arrayColumn(String column){
        List<String> list = new ArrayList<>();
        for(QueryRecord row : this){
            list.add(row.get(column));
        }
        return list;
    }

    public List<?> arrayColumn(String column, Class<?> clazz){
        List<Object> list = new ArrayList<>();
        for(QueryRecord row : this){
            switch (clazz.getSimpleName()) {
                case "Integer":
                    list.add(row.getAsInt(column));
                    break;
                case "Long":
                    list.add(row.getAsLong(column));
                    break;
                case "Boolean":
                    list.add(row.getAsBoolean(column));
                    break;
                case "Double":
                    list.add(row.getAsDouble(column));
                    break;
                default:
                    list.add(row.get(column));
                    break;
            }
        }
        return list;
    }


    public QueryResult shuffle(){
        java.util.Collections.shuffle(this);
        return this;
    }

    public QueryResult limit(int limit){
        QueryResult result = new QueryResult();
        for(int i = 0; i < limit; i++){
            result.add(this.get(i));
        }
        return result;
    }

    public Map<String, Object> groupBy(String... keys) {
        return groupByRecursive(this, 0, keys);
    }

    private Map<String, Object> groupByRecursive(QueryResult collection, int keyIndex, String... keys) {
        if (keyIndex >= keys.length) {
            return null;
        }

        Map<String, Object> grouped = new HashMap<>();
        String currentKey = keys[keyIndex];

        for (QueryRecord record : collection) {
            String keyValue = record.get(currentKey);
            grouped.computeIfAbsent(keyValue, k -> new QueryResult());
            ((QueryResult) grouped.get(keyValue)).add(record);
        }

        if (keyIndex < keys.length - 1) {
            for (Map.Entry<String, Object> entry : grouped.entrySet()) {
                entry.setValue(groupByRecursive((QueryResult) entry.getValue(), keyIndex + 1, keys));
            }
        }

        return grouped;
    }


}
