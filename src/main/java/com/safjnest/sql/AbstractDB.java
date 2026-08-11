package com.safjnest.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.utils.log.BotLogger;

public abstract class AbstractDB {
    protected abstract String getDatabase();

    public Connection getConnection() {
        try { return DatabaseHandler.getConnection(getDatabase());} 
        catch (SQLException e) { return null; }
    }

    public List<QueryRecord> query(String query) {
        Connection c = null;
        Statement stmt = null;
        List<QueryRecord> result = new ArrayList<>();

        try {
            c = DatabaseHandler.getConnection(getDatabase());
            if (c == null) throw new SQLException("Connection to the database failed!");
            stmt = c.createStatement();
            result = query(stmt, query);
            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    if (stmt != null) stmt.close();
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            throw new IllegalStateException("Query execution failed", ex);
        } finally {
            if (c != null) {
                try {
                    if (stmt != null) stmt.close();
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        return result;      
    }

    /**
     * Method used for returning rows from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public List<QueryRecord> query(Statement stmt, String query) throws SQLException {
        if (App.isTesting()) BotLogger.trace(query);
        List<QueryRecord> result;
        boolean hasResult = (stmt instanceof PreparedStatement pstmt)
                ? pstmt.execute()
                : stmt.execute(query, Statement.RETURN_GENERATED_KEYS);

        ResultSet resultSet = hasResult ? stmt.getResultSet() : stmt.getGeneratedKeys();
        try {
            result = elaborate(resultSet);
        } finally {
            if (resultSet != null) resultSet.close();
        }
        return result;
    }

    private List<QueryRecord> elaborate(ResultSet set) throws SQLException {
        return QueryRecordParser.fromRows(set);
    }

    public List<List<QueryRecord>> queries(String... queries) {
        List<List<QueryRecord>> results = new ArrayList<>();
        Connection c = null;
        Statement stmt = null;

        try {
            c = DatabaseHandler.getConnection(getDatabase());
            if (c == null) throw new SQLException("Connection to the database failed!");

            stmt = c.createStatement();

            for (String q : queries) {
                List<QueryRecord> result = query(stmt, q);
                results.add(result);
            }

            c.commit();
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    if (stmt != null) stmt.close();
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    if (stmt != null) stmt.close();
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryRecord lineQuery(String query) {
        try { return query(query).get(0); } 
        catch (Exception e) { return new QueryRecord(); }
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query.
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryRecord lineQuery(Statement stmt, String query) throws SQLException {
        try { return query(stmt, query).get(0); } 
        catch (Exception e) { return null;}
    }


    /**
     * Run one or more queries using the default statement
     * @param queries
     */
    public boolean defaultQuery(String... queries) {
        if (queries == null || queries.length == 0) return false;
        try {
            for (String query : queries) query(query);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
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
            query(stmt, query);
    }

    public List<QueryRecord> insert(String table, LinkedHashMap<String, Object> values) {
        List<QueryRecord> result = new ArrayList<>();
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
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return result;
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
