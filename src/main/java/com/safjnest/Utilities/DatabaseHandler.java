package com.safjnest.Utilities;

public class DatabaseHandler {
    static PostgreSQL sql;

    public DatabaseHandler(PostgreSQL sql) {
        DatabaseHandler.sql = sql;
    }

    public static PostgreSQL getSql() {
        return sql;
    }

	public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}
}
