package com.safjnest.mongo;

public final class MongoSettings {

    public static final String DEFAULT_URI =
        "mongodb://localhost:27017/?connectTimeoutMS=2000&serverSelectionTimeoutMS=2000";
    public static final String DEFAULT_DATABASE = "beebot";

    private static final String URI_PROPERTY = "beebot.mongo.uri";
    private static final String DATABASE_PROPERTY = "beebot.mongo.database";
    private static final String URI_ENVIRONMENT = "BEEBOT_MONGO_URI";
    private static final String DATABASE_ENVIRONMENT = "BEEBOT_MONGO_DATABASE";

    private MongoSettings() {}

    public static String getUri() {
        return getValue(URI_PROPERTY, URI_ENVIRONMENT, DEFAULT_URI);
    }

    public static String getDatabase() {
        return getValue(DATABASE_PROPERTY, DATABASE_ENVIRONMENT, DEFAULT_DATABASE);
    }

    private static String getValue(String property, String environment, String defaultValue) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue;

        String environmentValue = System.getenv(environment);
        return environmentValue != null && !environmentValue.isBlank()
            ? environmentValue
            : defaultValue;
    }
}
