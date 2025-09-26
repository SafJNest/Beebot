# Redis Setup for BeeBot Cache

This project has been updated to use Redis instead of Caffeine for caching. 

## Installation

### Docker (Recommended)
```bash
# Run Redis in Docker
docker run --name beebot-redis -p 6379:6379 -d redis:latest

# Or with persistence
docker run --name beebot-redis -p 6379:6379 -v redis-data:/data -d redis:latest
```

### Local Installation

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

#### macOS
```bash
brew install redis
brew services start redis
```

#### Windows
Download and install from: https://github.com/microsoftarchive/redis/releases

## Configuration

The application will connect to Redis using these environment variables:

- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)  
- `REDIS_PASSWORD` - Redis password (optional)

Example:
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=your_password_here
```

## Verification

Test your Redis connection:
```bash
redis-cli ping
# Should return: PONG
```

## Features

- **Connection Pooling**: Uses Jedis connection pool for efficient connection management
- **TTL Support**: Maintains expiration functionality from Caffeine
- **Type Safety**: Preserves type-safe operations from original cache system
- **Error Handling**: Graceful degradation if Redis is unavailable
- **Serialization**: Automatic Java object serialization for Redis storage

## Migration Notes

- All existing cache implementations (SoundCache, UserCache, GuildCache, GenericCache) work unchanged
- The cache interface remains the same - only the underlying storage changed
- All cached objects must be Serializable (UserData, Sound, Tag, AliasData, GuildData are already updated)