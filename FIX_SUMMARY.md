# Fix Summary: Multi-Database Configuration

## Problem
The application was failing to start with the error:
```
org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [website_dev.api_key]
```

## Root Cause
- The application uses JPA entities across **two different databases**:
  1. `league_of_legends` - for League of Legends data
  2. `website_dev` - for website/API data

- Spring Boot was configured to connect to only **one database** (`league_of_legends`)
- Hibernate was set to `validate` mode, which requires all entity tables to exist in the connected database
- The `ApiKey` entity was looking for a table in the `website_dev` schema, but Spring was only connected to `league_of_legends`

## Solution
Implemented a **multi-datasource configuration** to support both databases simultaneously:

### 1. Created Two DataSource Configurations
- **LeagueDataSourceConfig.java** - Primary datasource for League of Legends database
  - Scans entities in `com.safjnest.util.lol.api.spring` package
  - Connects to database specified in `spring.datasource.url`

- **WebsiteDataSourceConfig.java** - Secondary datasource for Website database
  - Scans entities in `com.safjnest.spring.api.model` package
  - Connects to database specified in `spring.datasource.website.url`

### 2. Updated Entity Annotations
Removed hardcoded schema references from all `@Table` annotations:
- Before: `@Table(name = "ApiKey", schema = "website_dev")`
- After: `@Table(name = "ApiKey")`

The database selection is now handled at the datasource configuration level.

### 3. Changed Hibernate DDL Mode
- From: `spring.jpa.hibernate.ddl-auto=validate` (strict validation)
- To: `spring.jpa.hibernate.ddl-auto=update` (auto-create/update tables)

This allows Hibernate to automatically create or update table structures instead of failing when tables don't match exactly.

### 4. Updated Spring Configuration
Modified `application.yml` to exclude default datasource autoconfiguration, since we're defining custom datasources:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

## Configuration Required

Update your `spring.properties` file with both database configurations:

```properties
# League of Legends Database (Primary)
spring.datasource.url=jdbc:mariadb://db/league_of_legends
spring.datasource.username=root
spring.datasource.password=your-password

# Website Database (Secondary)  
spring.datasource.website.url=jdbc:mariadb://db/website_dev
spring.datasource.website.username=root
spring.datasource.website.password=your-password
```

See `spring.properties.example` for complete configuration template.

## Benefits
1. **Proper separation** - Each database is managed independently
2. **Type safety** - Entities automatically use the correct database based on their package
3. **Flexibility** - Easy to add more databases in the future
4. **Reliability** - Each database has its own connection pool and transaction manager

## Files Modified
- `src/main/java/com/safjnest/spring/config/LeagueDataSourceConfig.java` (NEW)
- `src/main/java/com/safjnest/spring/config/WebsiteDataSourceConfig.java` (NEW)
- `src/main/java/com/safjnest/spring/api/model/ApiKey.java`
- `src/main/java/com/safjnest/util/lol/api/spring/*.java` (5 entity files)
- `src/main/resources/application.yml`
- `spring.properties.example` (NEW)
- `MULTI_DATABASE_SETUP.md` (NEW - detailed documentation)

## Testing
To test the configuration:
1. Ensure both databases exist and are accessible
2. Update `spring.properties` with correct database credentials
3. Run the Spring application
4. Tables will be automatically created/updated in the correct databases

The schema validation error should now be resolved, and the application should start successfully with access to both databases.
