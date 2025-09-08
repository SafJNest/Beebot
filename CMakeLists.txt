cmake_minimum_required(VERSION 3.16)
project(beebot C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Find required packages
find_package(PkgConfig REQUIRED)
find_package(Threads REQUIRED)

# Find libcurl for HTTP requests
find_package(CURL REQUIRED)

# Add include directories
include_directories(include)

# Source files
file(GLOB_RECURSE SOURCES "src/*.c")

# Exclude the config.c file since we're using simple_json.c instead
list(REMOVE_ITEM SOURCES "${CMAKE_SOURCE_DIR}/src/config.c")

# Create executable
add_executable(beebot ${SOURCES})

# Link libraries
target_link_libraries(beebot 
    ${CMAKE_THREAD_LIBS_INIT}
    ${CURL_LIBRARIES}
    m  # math library
)

# Compiler flags
target_compile_options(beebot PRIVATE -Wall -Wextra -g)

# Install target
install(TARGETS beebot DESTINATION bin)

# Copy configuration files
configure_file(${CMAKE_SOURCE_DIR}/config/config.json 
               ${CMAKE_BINARY_DIR}/config.json COPYONLY)