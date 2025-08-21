package com.safjnest.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.util.log.BotLogger;

public abstract class AbstractDB {
    protected abstract String getDatabase();

    public static HashMap<Long, List<String>> queryAnalytics = new HashMap<>();

    public Connection getConnection() {
        try { return DatabaseHandler.getConnection(getDatabase());} 
        catch (SQLException e) { return null; }
    }

    public QueryResult query(String query) {
        Connection c = null;
        Statement stmt = null;
        QueryResult result = new QueryResult();

        try {
            c = DatabaseHandler.getConnection(getDatabase());
            if (c == null) throw new SQLException("Connection to the database failed!");
            insertAnalytics(query);
            stmt = c.createStatement();
            result = query(stmt, query);
            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    stmt.close();
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    result.setSuccess(true);
                    stmt.close();
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        return result;      
    }

    /**
     * Method used for returning a {@link com.safjnest.sql.QueryResult result} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryResult query(Statement stmt, String query) throws SQLException {
        insertAnalytics(query);
        QueryResult result = new QueryResult();
        boolean hasResult = (stmt instanceof PreparedStatement pstmt)
                ? pstmt.execute()
                : stmt.execute(query, Statement.RETURN_GENERATED_KEYS);

        result = elaborate(hasResult ? stmt.getResultSet() : stmt.getGeneratedKeys());
        result.setAffectedRows(stmt.getUpdateCount());
        return result;
    }

    private QueryResult elaborate(ResultSet set) throws SQLException {
        QueryResult result = new QueryResult();
        ResultSetMetaData rsmd = set.getMetaData();
        while (set.next()) {
            QueryRecord row = new QueryRecord(set);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String key = rsmd.getColumnLabel(i);
                String valye = set.getString(i);
                row.put(key, valye);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryRecord lineQuery(String query) {

        Connection c = null;
        try {
            c = DatabaseHandler.getConnection(getDatabase());
        } catch (SQLException e) { }

        if (c == null) {
            BotLogger.error("[SQL] Connection to the database failed!");
        }
        QueryRecord beeRow = new QueryRecord(null);
        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            beeRow.setResultSet(rs);
            ResultSetMetaData rsmd = rs.getMetaData();
            if (rs.next()) {
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnName(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
            }
            //insertAnalytics(query);
            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        return beeRow;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query.
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryRecord lineQuery(Statement stmt, String query) throws SQLException {


        ResultSet rs = stmt.executeQuery(query);
        QueryRecord beeRow = new QueryRecord(rs);

        ResultSetMetaData rsmd = rs.getMetaData();

        if (rs.next()) {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
        }
        //insertAnalytics(query);
        return beeRow;
    }


    /**
     * Run one or more queries using the default statement
     * @param queries
     */
    public boolean defaultQuery(String... queries) {

        Connection c = null;
        try {
            c = DatabaseHandler.getConnection(getDatabase());
        } catch (SQLException e) { }

        if (c == null) {
            BotLogger.error("[SQL] Connection to the database failed!");
        }

        try (Statement stmt = c.createStatement()) {
            for (String query : queries)
                stmt.execute(query);
            //insertAnalytics(queries.toString());
            c.commit();
            return true;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return false;
    }

    public CompletableFuture<Void> runQueryAsync(String... queries) {
        return new ChronoTask() {
            @Override
            public void run() {
                defaultQuery(queries);
            }
        }.queueFuture();
    }


    /**
     * Run one or more queries with a specific statement
     * <p>
     * Only for insert, update and delete
     * @param stmt
     * @param queries
     * @throws SQLException
     */
    public void query(Statement stmt, String... queries) throws SQLException {

        for (String query : queries)
            stmt.execute(query);

        //insertAnalytics(queries.toString());

    }

    public QueryResult insert(String table, LinkedHashMap<String, Object> values) {
        QueryResult result = new QueryResult();
        StringJoiner fields = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        StringJoiner updates = new StringJoiner(", ");

        for (String key : values.keySet()) {
            fields.add(key);
            placeholders.add("?");
            updates.add(key + " = VALUES(" + key + ")");
        }

        String query = "INSERT INTO " + table + " (" + fields + ") VALUES (" + placeholders + ") " +
                    "ON DUPLICATE KEY UPDATE " + updates.toString();

        Connection c = getConnection();
        try (PreparedStatement pstmt = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            for (Object value : values.values()) {
                if (value == null) pstmt.setNull(index, java.sql.Types.NULL);
                 else if (value instanceof String) pstmt.setString(index, (String) value);
                 else if (value instanceof Integer) pstmt.setInt(index, (Integer) value);
                 else if (value instanceof Long) pstmt.setLong(index, (Long) value);
                 else if (value instanceof Double) pstmt.setDouble(index, (Double) value);
                 else if (value instanceof Float) pstmt.setFloat(index, (Float) value);
                 else if (value instanceof Boolean) pstmt.setBoolean(index, (Boolean) value);
                 else if (value instanceof java.sql.Date) pstmt.setDate(index, (java.sql.Date) value);
                 else if (value instanceof java.sql.Timestamp) pstmt.setTimestamp(index, (java.sql.Timestamp) value);
                 else pstmt.setObject(index, value);
                index++;
            }
            result = query(pstmt, query);
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    result.setSuccess(true);
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return result;
    }

        
    private void insertAnalytics(String query) {
        List<String> queries = queryAnalytics.getOrDefault(System.currentTimeMillis(), new ArrayList<>());
        queries.add(query);
        queryAnalytics.put(System.currentTimeMillis(), queries);
    }
    

    /**
     * @deprecated
     * What the actual fuck sunyx?
     * eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee la cannuccia
     * weru9fgt9uehrgferwfghreyuio
    */
    protected String getCannuccia() {
        return ":cannuccia:";
    }

}