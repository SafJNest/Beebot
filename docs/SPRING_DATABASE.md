# Spring Database Integration

This document describes the new Spring Data JPA integration added to Beebot.

## Overview

The bot now includes a modern Spring Data JPA layer alongside the existing manual SQL implementation. This provides:

- Type-safe database operations
- Automatic query generation
- Transaction management
- Connection pooling via HikariCP
- Entity relationship mapping

## Architecture

### Entities
- `Guild.java` - Represents Discord guild settings
- `Sound.java` - Represents sound files and metadata
- `Member.java` - Represents guild member data

### Repositories
- `GuildRepository` - Guild data access operations
- `SoundRepository` - Sound data access with complex queries
- `MemberRepository` - Member experience and level data

### Services
- `SoundService` - Updated to use JPA repositories
- `GuildService` - Mixed legacy and JPA approach
- `SoundManagementService` - New service demonstrating JPA best practices

### Configuration
- `DatabaseConfig` - HikariCP and JPA configuration
- `DatabaseInitializer` - Database startup verification

## Configuration

### application.yml
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MariaDBDialect
```

### spring.properties
Default Spring Boot properties for server and database configuration.

## Usage

### Basic Entity Operations
```java
@Autowired
private SoundRepository soundRepository;

// Find sound by ID
Optional<Sound> sound = soundRepository.findById(123);

// Create new sound
Sound newSound = new Sound();
newSound.setName("example");
soundRepository.save(newSound);
```

### Custom Queries
The repositories include custom queries for complex operations:
- User authorization checks
- Paginated results
- Random selection
- Full-text search

### Transaction Management
All service operations are automatically wrapped in transactions using `@Transactional`.

## Migration Path

The integration maintains backward compatibility:
1. Existing `BotDB` class continues to work
2. New Spring components can be gradually adopted
3. Services can mix both approaches during transition

## Benefits

1. **Type Safety** - Compile-time checking of database operations
2. **Performance** - Optimized queries and connection pooling
3. **Maintainability** - Standard Spring patterns and conventions
4. **Testing** - Easy to mock repositories for unit tests
5. **Monitoring** - Built-in metrics and health checks

## Next Steps

1. Migrate remaining database operations to JPA
2. Add database migration scripts
3. Implement comprehensive testing
4. Add monitoring and metrics
5. Consider adding database caching layer