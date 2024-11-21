package com.safjnest.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueryCollection extends ArrayList<QueryRecord> {

    public QueryCollection(){
        super();
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


    public QueryCollection shuffle(){
        java.util.Collections.shuffle(this);
        return this;
    }

    public QueryCollection limit(int limit){
        QueryCollection result = new QueryCollection();
        for(int i = 0; i < limit; i++){
            result.add(this.get(i));
        }
        return result;
    }


}
