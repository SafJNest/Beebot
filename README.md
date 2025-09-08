# Beebot - Discord Bot in C

![Beebot Logo](rsc/assets/logo_cropped.png)

A complete rewrite of the Java JDA Discord bot in pure C, maintaining all original functionality while using native C libraries and manual memory management.

## Overview

This project is a comprehensive conversion of a feature-rich Discord bot from Java (using JDA - Java Discord API) to pure C. The bot maintains the same behavior and functionality as the original while utilizing C libraries and proper memory management practices.

### Original Java Bot Features Converted

- **151+ Commands** across 9 categories
- **Audio System** - Music playback, custom sounds, TTS
- **Moderation Tools** - Ban, kick, mute, message clearing
- **Server Management** - Channel info, member management, permissions
- **Gaming Integration** - League of Legends stats and data
- **Utility Commands** - Calculator, dice, weather, QR codes
- **Custom Features** - Soundboards, playlists, user tracking

## Key Conversion Differences

| Java (Original) | C (Converted) |
|----------------|---------------|
| Garbage Collection | Manual Memory Management |
| JDA Library | Direct HTTP/WebSocket + Discord C Libraries |
| Object-Oriented Commands | Function Pointer Callbacks |
| Exception Handling | Explicit Error Codes |
| Spring Boot Configuration | JSON Configuration Files |
| Maven Build System | CMake Build System |

## Building and Installation

### Prerequisites

- GCC or Clang compiler with C11 support
- CMake 3.16 or later
- libcurl (for HTTP requests)
- pthread library

### Ubuntu/Debian
```bash
sudo apt-get update
sudo apt-get install build-essential cmake libcurl4-openssl-dev
```

### Building

```bash
# Create build directory
mkdir build && cd build

# Configure with CMake
cmake ..

# Build the project
make

# Run the bot
./beebot
```

## Configuration

Create a `config.json` file with your bot settings:

```json
{
    "token": "YOUR_DISCORD_BOT_TOKEN_HERE",
    "prefix": "!",
    "owner_id": "YOUR_DISCORD_USER_ID",
    "debug_mode": 1
}
```

## Usage

```bash
# Basic usage
./beebot

# With custom config file
./beebot -c /path/to/config.json

# With debug mode
./beebot --debug
```

### Available Commands

#### Core Commands
- `!ping` - Check bot latency
- `!help` - Show available commands
- `!shutdown` - Stop the bot (owner only)

#### Audio Commands
- `!connect` - Join voice channel
- `!play <url/search>` - Play music from YouTube
- `!stop` - Stop playback and clear queue

#### Moderation Commands
- `!ban <@user> [reason]` - Ban a user
- `!kick <@user> [reason]` - Kick a user
- `!clear <amount>` - Clear messages

## Architecture

The C version implements proper memory management and uses function pointers for the command system, maintaining the same functionality as the original Java bot while offering improved performance.

## Credits

### Original Java Bot Authors
- **NeutronSun** - [GitHub](https://github.com/NeutronSun)
- **Leon412** - [GitHub](https://github.com/Leon412)

### C Conversion
- Maintains original functionality and behavior
- Converted from Java JDA to pure C implementation
- Optimized for performance and memory efficiency
