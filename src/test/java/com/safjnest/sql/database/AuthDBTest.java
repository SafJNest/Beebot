package com.safjnest.sql.database;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AuthDBTest {
    @Test
    public void selectsAuthDatabase() {
        assertEquals("auth", AuthDB.get().getDatabase());
    }
}
