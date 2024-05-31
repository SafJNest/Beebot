package com.safjnest.sql;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class QueryResult implements Iterable<ResultRow>{

    private List<ResultRow> result;

    public QueryResult(){
        this.result = new ArrayList<>();
    }

    @Override
    public Iterator<ResultRow> iterator() {
        return result.iterator();
    }

    public void add(ResultRow row){
        result.add(row);
    }

    public ResultRow get(int index){
        return result.get(index);
    }

    public int size(){
        return result.size();
    }

    public boolean isEmpty(){
        return result.isEmpty();
    }

    public int getAffectedRows(){
        return result.size();
    }

    public List<Map<String, String>> toList(){
        List<Map<String, String>> list = new ArrayList<>();
        for(ResultRow row : result){
            list.add(row.getAsMap());
        }
        return list;
    }

    public List<String> arrayColumn(String column){
        List<String> list = new ArrayList<>();
        for(ResultRow row : result){
            list.add(row.get(column));
        }
        return list;
    }

    public Stream<ResultRow> stream() {
        return result.stream();
    }

    @Override
    public String toString() {
        return result.toString();
    }


}
