# Quick Start Guide - Multi-Database Setup

This guide will help you quickly set up the multi-database configuration.

## Prerequisites
- MariaDB/MySQL server running
- Two databases created:
  - `league_of_legends` (or your preferred name)
  - `website_dev` (or your preferred name)

## Setup Steps

### 1. Create the Configuration File

Copy the example configuration:
```bash
cp spring.properties.example spring.properties
```

### 2. Edit spring.properties

Update the database connection details:

```properties
# League of Legends Database
spring.datasource.url=jdbc:mariadb://your-host:3306/league_of_legends
spring.datasource.username=your-username
spring.datasource.password=your-password

# Website Database
spring.datasource.website.url=jdbc:mariadb://your-host:3306/website_dev
spring.datasource.website.username=your-username
spring.datasource.website.password=your-password

# JWT Secret (generate a secure random string)
jwt.secret=your-secret-key-here
```

### 3. Create the Databases (if not exists)

Connect to your MariaDB/MySQL server and run:
```sql
CREATE DATABASE IF NOT EXISTS league_of_legends;
CREATE DATABASE IF NOT EXISTS website_dev;
```

### 4. Run the Application

The application will automatically:
- Connect to both databases
- Create tables if they don't exist
- Update table structures if needed

## What Happens Automatically

When you start the application with the correct configuration:

1. **League Database Tables** (in `league_of_legends` database):
   - `summoner`
   - `match`
   - `participant`
   - `rank`
   - `masteries`

2. **Website Database Tables** (in `website_dev` database):
   - `ApiKey`

## Troubleshooting

### Error: "Access denied for user"
- Check username and password in `spring.properties`
- Ensure the database user has appropriate permissions

### Error: "Unknown database"
- Ensure both databases exist
- Check database names match in `spring.properties`

### Error: "Communications link failure"
- Check if database server is running
- Verify host and port in connection string
- Check firewall rules

### Tables not created
- With `hibernate.ddl-auto=update`, tables should be created automatically
- Check application logs for Hibernate errors
- Verify user has CREATE TABLE permissions

## Verification

To verify the setup is working:

1. Start the application
2. Check the logs for:
   - "HikariPool" connection messages for both datasources
   - No errors about missing tables
   - Successful application startup

3. Connect to your databases and verify tables exist:
```sql
USE league_of_legends;
SHOW TABLES;

USE website_dev;
SHOW TABLES;
```

## Database Schema Updates

When you modify entity classes:
- Tables will be automatically updated on next application start
- Columns will be added/modified as needed
- **Note**: Columns are NOT automatically removed (for safety)

If you need a fresh start:
```sql
DROP DATABASE league_of_legends;
DROP DATABASE website_dev;
CREATE DATABASE league_of_legends;
CREATE DATABASE website_dev;
```

Then restart the application.

## Need Help?

- See `MULTI_DATABASE_SETUP.md` for detailed technical documentation
- See `FIX_SUMMARY.md` for explanation of what was fixed and why
- Check Spring Boot logs for specific error messages
