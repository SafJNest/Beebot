package com.safjnest.database.sql.database;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.safjnest.database.sql.QueryRecord;

public class BotDBCacheRowsTest {

    @Test
    public void bindsIdsAndDeduplicatesThem() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:cache-bindings")) {
            connection.createStatement().execute("CREATE TABLE alias (id VARCHAR PRIMARY KEY, name VARCHAR)");
            String special = "x' OR '1'='1";
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO alias VALUES (?, ?)")) {
                insert.setString(1, special);
                insert.setString(2, "selected");
                insert.executeUpdate();
                insert.setString(1, "other");
                insert.setString(2, "excluded");
                insert.executeUpdate();
            }
            List<QueryRecord> rows = BotDB.cacheRows(connection, "SELECT id, name FROM alias WHERE id IN (%s)", List.of(special, special));
            assertEquals(1, rows.size());
            assertEquals(special, rows.get(0).get("id"));
            assertEquals("selected", rows.get(0).get("name"));
        }
    }

    @Test
    public void batchesLargeRequestsAndUsesQueryRecords() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:cache-batches")) {
            connection.createStatement().execute("CREATE TABLE guild (guild_id VARCHAR PRIMARY KEY)");
            List<String> ids = new ArrayList<>();
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO guild VALUES (?)")) {
                for (int index = 0; index < 1001; index++) {
                    ids.add(String.valueOf(index));
                    insert.setString(1, String.valueOf(index));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            AtomicInteger queries = new AtomicInteger();
            Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement"))
                        queries.incrementAndGet();
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
            List<QueryRecord> rows = BotDB.cacheRows(counted, "SELECT guild_id FROM guild WHERE guild_id IN (%s)", ids);
            assertEquals(1001, rows.size());
            assertEquals(3, queries.get());
            assertNotNull(rows.get(0).get("guild_id"));
            assertTrue(BotDB.cacheRows(counted, "SELECT guild_id FROM guild WHERE guild_id IN (%s)", List.of()).isEmpty());
            assertEquals(3, queries.get());
        }
    }

    @Test
    public void propagatesStorageErrors() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:cache-errors")) {
            assertThrows(SQLException.class,
                () -> BotDB.cacheRows(connection, "SELECT id FROM missing_table WHERE id IN (%s)", List.of("1")));
        }
    }
}
