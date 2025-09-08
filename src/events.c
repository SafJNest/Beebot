/**
 * Event handlers for Beebot
 * 
 * Handles Discord events like ready, message received, etc.
 * Converted from Java JDA event listeners to C callback functions.
 */

#include "beebot.h"

extern discord_client_t *g_bot;

void event_on_ready(discord_client_t *client) {
    if (!client) return;
    
    log_info("Bot is ready and connected to Discord!");
    log_info("Bot ID: %s", client->bot_id ? client->bot_id : "Unknown");
    
    /* In a real implementation, this would:
     * - Update bot presence/status
     * - Cache guild information
     * - Initialize any background tasks
     */
    
    log_info("=== BEEBOT READY ===");
    log_info("Prefix: %s", client->config->prefix);
    log_info("Commands loaded: (counting...)");
    
    /* Count commands */
    int command_count = 0;
    command_t *current = client->commands;
    while (current) {
        command_count++;
        current = current->next;
    }
    log_info("Total commands: %d", command_count);
}

void event_on_message(discord_client_t *client, const char *channel_id, 
                     const char *user_id, const char *message) {
    if (!client || !channel_id || !user_id || !message) return;
    
    /* Ignore bot messages (in real implementation, check if user_id is bot) */
    if (client->bot_id && strcmp(user_id, client->bot_id) == 0) {
        return;
    }
    
    log_debug("Message received - Channel: %s, User: %s, Content: %s", 
              channel_id, user_id, message);
    
    /* Check if message starts with prefix */
    if (!client->config->prefix || strncmp(message, client->config->prefix, strlen(client->config->prefix)) != 0) {
        /* Not a command, ignore for now */
        return;
    }
    
    /* Extract command and arguments */
    const char *command_text = message + strlen(client->config->prefix);
    char *command_copy = strdup(command_text);
    if (!command_copy) {
        log_error("Failed to allocate memory for command parsing");
        return;
    }
    
    /* Trim whitespace */
    char *trimmed = string_trim(command_copy);
    
    /* Split into command and arguments */
    int argc = 0;
    char **argv = string_split(trimmed, " ", &argc);
    
    if (argc == 0) {
        free(command_copy);
        return;
    }
    
    /* Find and execute command */
    command_t *cmd = command_find(argv[0]);
    if (cmd) {
        log_info("Executing command: %s (from user %s in channel %s)", 
                 argv[0], user_id, channel_id);
        
        /* Check owner-only restriction */
        if (cmd->owner_only) {
            if (!client->config->owner_id || strcmp(user_id, client->config->owner_id) != 0) {
                log_info("Command %s requires owner permissions", argv[0]);
                free(command_copy);
                string_array_free(argv, argc);
                return;
            }
        }
        
        /* Execute command with arguments (excluding command name) */
        const char **args = argc > 1 ? (const char**)&argv[1] : NULL;
        int arg_count = argc > 1 ? argc - 1 : 0;
        
        cmd->callback(client, channel_id, user_id, message, args, arg_count);
    } else {
        log_debug("Unknown command: %s", argv[0]);
        /* In a real implementation, you might send an error message */
    }
    
    free(command_copy);
    string_array_free(argv, argc);
}

/* Additional event handlers that could be implemented */

void event_on_guild_join(discord_client_t *client, const char *guild_id) {
    if (!client || !guild_id) return;
    
    log_info("Bot joined guild: %s", guild_id);
    
    /* In the original Java bot, this would:
     * - Initialize guild cache data
     * - Set up default permissions
     * - Send welcome message if configured
     */
}

void event_on_guild_leave(discord_client_t *client, const char *guild_id) {
    if (!client || !guild_id) return;
    
    log_info("Bot left guild: %s", guild_id);
    
    /* In the original Java bot, this would:
     * - Clean up guild cache data
     * - Remove guild-specific settings
     */
}

void event_on_member_join(discord_client_t *client, const char *guild_id, const char *user_id) {
    if (!client || !guild_id || !user_id) return;
    
    log_info("Member %s joined guild %s", user_id, guild_id);
    
    /* In the original Java bot, this would:
     * - Send welcome message if configured
     * - Assign auto-roles
     * - Update member cache
     */
}

void event_on_member_leave(discord_client_t *client, const char *guild_id, const char *user_id) {
    if (!client || !guild_id || !user_id) return;
    
    log_info("Member %s left guild %s", user_id, guild_id);
    
    /* In the original Java bot, this would:
     * - Send leave message if configured
     * - Clean up user-specific data
     */
}

void event_on_voice_state_update(discord_client_t *client, const char *guild_id, 
                                 const char *user_id, const char *old_channel, const char *new_channel) {
    if (!client || !guild_id || !user_id) return;
    
    if (old_channel && new_channel) {
        log_debug("User %s moved from channel %s to %s in guild %s", 
                  user_id, old_channel, new_channel, guild_id);
    } else if (new_channel) {
        log_debug("User %s joined voice channel %s in guild %s", 
                  user_id, new_channel, guild_id);
    } else if (old_channel) {
        log_debug("User %s left voice channel %s in guild %s", 
                  user_id, old_channel, guild_id);
    }
    
    /* In the original Java bot, this would:
     * - Handle auto-disconnect when voice channel is empty
     * - Track voice activity for audio commands
     * - Update voice state cache
     */
}