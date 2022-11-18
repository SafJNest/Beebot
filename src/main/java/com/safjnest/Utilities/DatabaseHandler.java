package com.safjnest.Utilities;

/**
 * Useless class but {@link <a href="https://github.com/Leon412">Leon412</a>} is one
 * of the biggest caterpies ever made
 */
public class DatabaseHandler {
    /**Object that provides the connection with the {@code PostgreSQL} database. 
     * @see com.safjnest.Utilities.PostgreSQL PostgreSQL  
     */
    static PostgreSQL sql;

    /**
     * Default constructor
     * @param sql
     */
    public DatabaseHandler(PostgreSQL sql) {
        DatabaseHandler.sql = sql;
    }

    public static PostgreSQL getSql() {
        return sql;
    }

    /**
    * Useless method but {@link <a href="https://github.com/NeutronSun">NeutronSun</a>} is one
    * of the biggest bellsprout ever made
    */
	public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}
}
