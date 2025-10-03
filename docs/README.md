# Beebot Documentation

This directory contains comprehensive documentation for Beebot's features and systems.

## Available Documentation

### Advanced Stats System

#### [STATS_SYSTEM_ANALYSIS.md](STATS_SYSTEM_ANALYSIS.md)
Comprehensive analysis of the advanced statistics system including:
- Current system architecture and limitations
- Industry analysis (OP.GG, Mobalytics, LOLytics)
- Comparison matrix and benchmarks
- Proposed enhancements and implementation phases
- Technical specifications and database schema
- Performance targets and optimization strategies

**Topics Covered:**
- Data storage strategies
- Statistics calculation methods
- Caching architecture
- Database optimizations
- Implementation roadmap

#### [ADVANCED_STATS_USAGE.md](ADVANCED_STATS_USAGE.md)
Complete usage guide for the advanced statistics system including:
- Quick start guide
- Filtering examples (time, queue, lane, rank, champion, region)
- Accessing different types of statistics
- Cache management
- Best practices
- Integration examples (Discord bot, REST API)
- Troubleshooting guide
- Migration guide from old system

**Topics Covered:**
- Basic usage patterns
- Filter composition
- Statistics access methods
- Performance considerations
- Code examples and snippets

## Quick Links

### For Developers
- [Stats System Analysis](STATS_SYSTEM_ANALYSIS.md) - Architecture and design decisions
- [Usage Guide](ADVANCED_STATS_USAGE.md) - Implementation examples

### For Users
- [Usage Guide](ADVANCED_STATS_USAGE.md) - How to use the stats features

## Documentation Structure

```
docs/
├── README.md                       # This file
├── STATS_SYSTEM_ANALYSIS.md       # System architecture and analysis
└── ADVANCED_STATS_USAGE.md        # Usage guide and examples
```

## Contributing

When adding new documentation:

1. **Keep it organized**: Use clear headings and sections
2. **Provide examples**: Include code snippets and use cases
3. **Update this README**: Add links to new documentation
4. **Be comprehensive**: Cover both usage and implementation details
5. **Keep it current**: Update docs when code changes

## Related Resources

### Database Schema
- `database/league_of_legends/` - SQL schema definitions
- `database/league_of_legends/optimizations.sql` - Performance indexes

### Source Code
- `src/main/java/com/safjnest/sql/database/LeagueDB.java` - Database access layer
- `src/main/java/com/safjnest/core/cache/managers/StatsCache.java` - Cache implementation
- `src/main/java/com/safjnest/util/lol/model/PlayerStats.java` - Stats model
- `src/main/java/com/safjnest/util/lol/model/AdvancedStatsFilter.java` - Filter model

## System Overview

### Advanced Stats System Features

✅ **Comprehensive Statistics**
- Basic stats (games, wins, losses, win rate)
- KDA metrics (kills, deaths, assists, KDA ratio)
- Efficiency metrics (CSM, GPM, DPM, DPG, VSPM)
- Performance indicators (streak, duration, KP%)
- Champion and lane breakdowns
- Recent form tracking

✅ **Advanced Filtering**
- Time period filtering
- Queue type filtering
- Lane role filtering
- Rank range filtering
- Region filtering
- Champion filtering
- Composite filters

✅ **Performance Optimization**
- In-memory caching (Caffeine)
- Automatic cache invalidation
- Database query optimization
- Composite indexes
- 10x faster retrieval

### Architecture Highlights

**Cache Strategy:**
- 15-minute TTL for active players
- Stale data detection
- LRU eviction policy
- 10,000 entry limit

**Database Optimization:**
- Composite indexes for common queries
- Materialized view support (future)
- Query result pagination
- Efficient aggregation

**API Design:**
- Fluent filter builder
- Type-safe enum parameters
- Immutable statistics objects
- Cache-first retrieval

## Version History

### v10.1 (Current)
- ✅ Advanced statistics system
- ✅ Enhanced filtering (rank, region, lane)
- ✅ Stats caching with Caffeine
- ✅ Database optimizations
- ✅ Comprehensive documentation

### Future Versions
- v10.2: Matchup statistics
- v10.3: Build path analysis
- v10.4: Performance predictions (ML)
- v10.5: Meta analysis and trends

## Support

For questions or issues:
- Review the documentation in this directory
- Check source code comments
- Contact the development team

## License

This documentation is part of the Beebot project and follows the same license.
