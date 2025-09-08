#ifndef BEEBOT_H
#define BEEBOT_H

#define _GNU_SOURCE  /* For strdup and strcasecmp */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <time.h>
#include <sys/time.h>
#include <strings.h>  /* For strcasecmp */

/* Optional HTTP support - uncomment if libcurl is available */
/* #include <curl/curl.h> */
/* #define HTTP_SUPPORT */

/* Forward declarations */
typedef struct bot_config bot_config_t;
typedef struct command command_t;
typedef struct discord_client discord_client_t;

/* Bot configuration structure */
struct bot_config {
    char *token;
    char *prefix;
    char *owner_id;
    int max_commands;
    int debug_mode;
};

/* Command callback function type */
typedef void (*command_callback_t)(discord_client_t *client, const char *channel_id, 
                                  const char *user_id, const char *message, const char **args, int argc);

/* Command structure */
struct command {
    char *name;
    char **aliases;
    int alias_count;
    char *description;
    char *usage;
    char *category;
    int cooldown;
    int owner_only;
    int hidden;
    command_callback_t callback;
    struct command *next; /* Linked list */
};

/* Discord client structure */
struct discord_client {
    char *token;
    char *bot_id;
    bot_config_t *config;
    command_t *commands;
    pthread_t bot_thread;
    int running;
};

/* Function prototypes */

/* Bot lifecycle */
int bot_init(bot_config_t *config);
int bot_start(void);
void bot_stop(void);
void bot_cleanup(void);
discord_client_t* bot_get_instance(void);

/* Configuration */
bot_config_t* config_load(const char *filename);
void config_free(bot_config_t *config);

/* Commands */
void command_register(const char *name, const char **aliases, int alias_count,
                     const char *description, const char *usage, const char *category,
                     int cooldown, int owner_only, int hidden, command_callback_t callback);
command_t* command_find(const char *name);
void commands_init(void);
void commands_cleanup(void);

/* Events */
void event_on_ready(discord_client_t *client);
void event_on_message(discord_client_t *client, const char *channel_id, 
                     const char *user_id, const char *message);

/* Utilities */
char** string_split(const char *str, const char *delimiter, int *count);
void string_array_free(char **array, int count);
char* string_trim(char *str);
void log_info(const char *format, ...);
void log_error(const char *format, ...);
void log_debug(const char *format, ...);

/* HTTP utilities - conditional compilation */
#ifdef HTTP_SUPPORT
typedef struct {
    char *memory;
    size_t size;
} http_response_t;

int http_get(const char *url, http_response_t *response);
int http_post(const char *url, const char *data, http_response_t *response);
void http_response_free(http_response_t *response);
#endif

#endif /* BEEBOT_H */