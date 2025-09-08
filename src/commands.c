/**
 * Command system for Beebot
 * 
 * Handles command registration, lookup, and execution.
 * Converted from Java Command/SlashCommand pattern to C function pointers.
 */

#include "beebot.h"

/* Global command list head */
static command_t *g_commands_head = NULL;
extern discord_client_t *g_bot;

/* Command implementations - forward declarations */
void cmd_ping(discord_client_t *client, const char *channel_id, const char *user_id, 
              const char *message, const char **args, int argc);
void cmd_help(discord_client_t *client, const char *channel_id, const char *user_id, 
              const char *message, const char **args, int argc);
void cmd_shutdown(discord_client_t *client, const char *channel_id, const char *user_id, 
                  const char *message, const char **args, int argc);

void command_register(const char *name, const char **aliases, int alias_count,
                     const char *description, const char *usage, const char *category,
                     int cooldown, int owner_only, int hidden, command_callback_t callback) {
    
    if (!name || !callback) {
        log_error("Invalid command registration parameters");
        return;
    }
    
    command_t *cmd = malloc(sizeof(command_t));
    if (!cmd) {
        log_error("Failed to allocate memory for command: %s", name);
        return;
    }
    
    memset(cmd, 0, sizeof(command_t));
    
    /* Set command properties */
    cmd->name = strdup(name);
    cmd->description = description ? strdup(description) : strdup("No description");
    cmd->usage = usage ? strdup(usage) : strdup("");
    cmd->category = category ? strdup(category) : strdup("General");
    cmd->cooldown = cooldown;
    cmd->owner_only = owner_only;
    cmd->hidden = hidden;
    cmd->callback = callback;
    
    /* Copy aliases */
    if (aliases && alias_count > 0) {
        cmd->aliases = malloc(alias_count * sizeof(char*));
        if (cmd->aliases) {
            cmd->alias_count = alias_count;
            for (int i = 0; i < alias_count; i++) {
                cmd->aliases[i] = strdup(aliases[i]);
            }
        }
    }
    
    /* Add to linked list */
    cmd->next = g_commands_head;
    g_commands_head = cmd;
    
    log_debug("Registered command: %s", name);
}

command_t* command_find(const char *name) {
    if (!name) return NULL;
    
    command_t *current = g_commands_head;
    while (current) {
        /* Check command name */
        if (strcasecmp(current->name, name) == 0) {
            return current;
        }
        
        /* Check aliases */
        for (int i = 0; i < current->alias_count; i++) {
            if (strcasecmp(current->aliases[i], name) == 0) {
                return current;
            }
        }
        
        current = current->next;
    }
    
    return NULL;
}

/* External command module initializers */
void audio_commands_init(void);
void moderation_commands_init(void);

void commands_init(void) {
    log_info("Initializing commands...");
    
    /* Register core commands */
    const char *ping_aliases[] = {"pong", "latency"};
    command_register("ping", ping_aliases, 2, "Check bot latency", "ping", "Misc", 2, 0, 0, cmd_ping);
    
    const char *help_aliases[] = {"h", "commands"};
    command_register("help", help_aliases, 2, "Show available commands", "help [command]", "Misc", 0, 0, 0, cmd_help);
    
    const char *shutdown_aliases[] = {"stop", "exit"};
    command_register("shutdown", shutdown_aliases, 2, "Shutdown the bot", "shutdown", "Owner", 0, 1, 1, cmd_shutdown);
    
    /* Initialize command modules */
    audio_commands_init();
    moderation_commands_init();
    
    log_info("Commands initialized successfully");
}

void commands_cleanup(void) {
    command_t *current = g_commands_head;
    command_t *next;
    
    while (current) {
        next = current->next;
        
        /* Free command data */
        if (current->name) free(current->name);
        if (current->description) free(current->description);
        if (current->usage) free(current->usage);
        if (current->category) free(current->category);
        
        /* Free aliases */
        if (current->aliases) {
            for (int i = 0; i < current->alias_count; i++) {
                if (current->aliases[i]) free(current->aliases[i]);
            }
            free(current->aliases);
        }
        
        free(current);
        current = next;
    }
    
    g_commands_head = NULL;
    log_debug("Commands cleanup completed");
}

/* Command implementations */

void cmd_ping(discord_client_t *client, const char *channel_id, const char *user_id, 
              const char *message, const char **args, int argc) {
    (void)user_id; (void)message; (void)args; (void)argc;  /* Suppress unused warnings */
    
    struct timeval start, end;
    gettimeofday(&start, NULL);
    
    /* Simulate Discord API call delay */
    usleep(50000);  /* 50ms */
    
    gettimeofday(&end, NULL);
    long latency_ms = ((end.tv_sec - start.tv_sec) * 1000) + ((end.tv_usec - start.tv_usec) / 1000);
    
    /* In a real implementation, this would send a message to the Discord channel */
    log_info("Pong! Latency: %ld ms (simulated response to channel %s)", latency_ms, channel_id);
}

void cmd_help(discord_client_t *client, const char *channel_id, const char *user_id, 
              const char *message, const char **args, int argc) {
    (void)client; (void)user_id; (void)message;  /* Suppress unused warnings */
    
    if (argc > 0) {
        /* Show help for specific command */
        command_t *cmd = command_find(args[0]);
        if (cmd) {
            log_info("Help for command '%s' (channel %s):", args[0], channel_id);
            log_info("  Description: %s", cmd->description);
            log_info("  Usage: %s", cmd->usage);
            log_info("  Category: %s", cmd->category);
            if (cmd->alias_count > 0) {
                log_info("  Aliases: ");
                for (int i = 0; i < cmd->alias_count; i++) {
                    log_info("    %s", cmd->aliases[i]);
                }
            }
        } else {
            log_info("Command '%s' not found (channel %s)", args[0], channel_id);
        }
    } else {
        /* Show all commands */
        log_info("Available commands (channel %s):", channel_id);
        
        command_t *current = g_commands_head;
        while (current) {
            if (!current->hidden) {
                log_info("  %s - %s", current->name, current->description);
            }
            current = current->next;
        }
    }
}

void cmd_shutdown(discord_client_t *client, const char *channel_id, const char *user_id, 
                  const char *message, const char **args, int argc) {
    (void)message; (void)args; (void)argc;  /* Suppress unused warnings */
    
    /* Check if user is bot owner */
    if (!client->config->owner_id || strcmp(user_id, client->config->owner_id) != 0) {
        log_info("Unauthorized shutdown attempt from user %s (channel %s)", user_id, channel_id);
        return;
    }
    
    log_info("Shutdown command received from owner (channel %s)", channel_id);
    bot_stop();
}