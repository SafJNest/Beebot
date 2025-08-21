package com.safjnest.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.util.log.BotLogger;

public abstract class AbstractDB {
    protected abstract String getDatabase();

    public Connection getConnection() {
        try { return DatabaseHandler.getConnection(getDatabase());} 
        catch (SQLException e) { return null; }
    }

    public QueryCollection query(String query) {
        Connection c = null;
        try {
            c = DatabaseHandler.getConnection(getDatabase());
        } catch (SQLException e) { }

        if (c == null) {
            BotLogger.error("[SQL] Connection to the database failed!");
        }

        QueryCollection result = new QueryCollection();

        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                QueryRecord beeRow = new QueryRecord(rs);
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
                result.add(beeRow);
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

        return result;      
    }

    /**
     * Method used for returning a {@link com.safjnest.sql.QueryCollection result} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public QueryCollection query(Statement stmt, String query) throws SQLException {

        QueryCollection result = new QueryCollection();

        ResultSet rs = stmt.executeQuery(query);
        ResultSetMetaData rsmd = rs.getMetaData();

        while (rs.next()) {
            QueryRecord beeRow = new QueryRecord(rs);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
            result.add(beeRow);
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
    

}