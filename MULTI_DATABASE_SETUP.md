# Multi-Database Configuration

This application uses multiple databases with Spring Boot's JPA support. The configuration has been set up to support two separate databases:

## Database Structure

1. **League of Legends Database** (Primary)
   - Database name: `league_of_legends`
   - Schema: `league_of_legends_test` (in entities)
   - Entities: `SummonerDTO`, `MatchDTO`, `ParticipantDTO`, `RankDTO`, `MasteriesDTO`
   - Package: `com.safjnest.util.lol.api.spring`

2. **Website Database** (Secondary)
   - Database name: `website_dev`
   - Schema: `website_dev` (in entities)
   - Entities: `ApiKey`
   - Package: `com.safjnest.spring.api.model`

## Configuration Files

### DataSource Configurations

Two configuration classes have been created to manage separate datasources:

1. **LeagueDataSourceConfig.java**
   - Manages the League of Legends database connection
   - Scans entities in `com.safjnest.util.lol.api.spring` package
   - Marked as `@Primary` (default datasource)
   - Persistence unit: `league`

2. **WebsiteDataSourceConfig.java**
   - Manages the Website database connection
   - Scans entities in `com.safjnest.spring.api.model` package
   - Persistence unit: `website`

### Spring Properties Configuration

The `spring.properties` file should include configuration for both databases:

```properties
# League of Legends Database (Primary)
spring.datasource.url=jdbc:mariadb://db/league_of_legends
spring.datasource.username=root
spring.datasource.password=

# Website Database (Secondary)
spring.datasource.website.url=jdbc:mariadb://db/website_dev
spring.datasource.website.username=root
spring.datasource.website.password=
```

See `spring.properties.example` for the complete configuration template.

## Key Changes Made

1. **Removed schema definitions from entities**: Previously entities had `@Table(name = "table_name", schema = "schema_name")`. The schema has been removed as it's now managed at the datasource level.

2. **Updated application.yml**: Added exclusions for default Spring Boot datasource autoconfiguration:
   ```yaml
   spring:
     autoconfigure:
       exclude:
         - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
         - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
   ```

3. **Changed ddl-auto mode**: Changed from `validate` to `update` to allow Hibernate to create/update tables automatically instead of just validating against existing schema.

## How It Works

1. **Separate DataSources**: Each database has its own HikariCP connection pool configured with the properties from `spring.properties`.

2. **Separate EntityManagers**: Each datasource has its own EntityManagerFactory that scans only the entities in its designated package.

3. **Separate TransactionManagers**: Each datasource has its own transaction manager for handling database transactions.

4. **Repository Scanning**: The `@EnableJpaRepositories` annotation on each configuration class ensures that repositories are associated with the correct datasource based on their package location.

## Usage

When injecting repositories or using entities:

- Entities from `com.safjnest.util.lol.api.spring` will use the League database
- Entities from `com.safjnest.spring.api.model` will use the Website database

The transaction managers and datasources are automatically selected based on the repository/entity package.

## Troubleshooting

If you encounter issues:

1. **Check database connectivity**: Ensure both databases are accessible with the provided credentials
2. **Verify table existence**: With `ddl-auto=update`, tables will be created automatically
3. **Check logs**: Enable `spring.jpa.show-sql=true` to see generated SQL queries
4. **Verify configuration**: Ensure `spring.properties` has correct configuration for both databases

## Migration from Single Database

If you were previously using a single database configuration:

1. Copy `spring.properties.example` to `spring.properties`
2. Update the database URLs, usernames, and passwords for both databases
3. Ensure both databases exist and are accessible
4. Remove any old `spring.jpa.hibernate.ddl-auto=validate` settings from properties files

The new configuration will automatically create tables in the correct databases if they don't exist.
