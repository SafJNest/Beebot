# Makefile for Beebot Discord Bot in C
# Alternative to CMake for simpler building

CC = gcc
CFLAGS = -std=c11 -Wall -Wextra -g -Iinclude
LDFLAGS = -lpthread -lm

# Optional: Enable HTTP support if libcurl is available
# Uncomment the next two lines if you have libcurl-dev installed
# CFLAGS += -DHTTP_SUPPORT
# LDFLAGS += -lcurl

# Directories
SRCDIR = src
INCDIR = include
BUILDDIR = build
CONFIGDIR = config

# Source files
SOURCES = $(wildcard $(SRCDIR)/*.c) $(wildcard $(SRCDIR)/commands/*.c)
OBJECTS = $(SOURCES:$(SRCDIR)/%.c=$(BUILDDIR)/%.o)
TARGET = beebot

# Default target
all: $(BUILDDIR) $(TARGET)

# Create build directory
$(BUILDDIR):
	mkdir -p $(BUILDDIR)
	mkdir -p $(BUILDDIR)/commands

# Main target
$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $(TARGET) $(LDFLAGS)

# Object files
$(BUILDDIR)/%.o: $(SRCDIR)/%.c
	$(CC) $(CFLAGS) -c $< -o $@

# Clean build files
clean:
	rm -rf $(BUILDDIR)
	rm -f $(TARGET)

# Install (copy to /usr/local/bin)
install: $(TARGET)
	sudo cp $(TARGET) /usr/local/bin/
	sudo mkdir -p /etc/beebot
	sudo cp $(CONFIGDIR)/config.json /etc/beebot/config.json.example

# Uninstall
uninstall:
	sudo rm -f /usr/local/bin/$(TARGET)
	sudo rm -rf /etc/beebot

# Debug build
debug: CFLAGS += -DDEBUG -O0
debug: $(TARGET)

# Release build
release: CFLAGS += -O2 -DNDEBUG
release: clean $(TARGET)

# Run the bot
run: $(TARGET)
	./$(TARGET)

# Run with debug
run-debug: $(TARGET)
	./$(TARGET) --debug

# Check for memory leaks (requires valgrind)
valgrind: $(TARGET)
	valgrind --leak-check=full --show-leak-kinds=all ./$(TARGET)

# Format code (requires clang-format)
format:
	clang-format -i $(SRCDIR)/*.c $(INCDIR)/*.h $(SRCDIR)/commands/*.c

# Check code style (requires cppcheck)
check:
	cppcheck --enable=all --std=c11 $(SRCDIR)

# Help
help:
	@echo "Beebot Discord Bot in C - Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all       - Build the bot (default)"
	@echo "  clean     - Remove build files"
	@echo "  debug     - Build with debug symbols"
	@echo "  release   - Build optimized release version"
	@echo "  install   - Install bot system-wide"
	@echo "  uninstall - Remove bot from system"
	@echo "  run       - Build and run the bot"
	@echo "  run-debug - Build and run with debug mode"
	@echo "  valgrind  - Run with memory leak detection"
	@echo "  format    - Format source code"
	@echo "  check     - Static code analysis"
	@echo "  help      - Show this help"
	@echo ""
	@echo "Dependencies:"
	@echo "  libcurl-dev, build-essential, pthread"
	@echo ""
	@echo "Ubuntu/Debian install:"
	@echo "  sudo apt-get install build-essential libcurl4-openssl-dev"

.PHONY: all clean install uninstall debug release run run-debug valgrind format check help