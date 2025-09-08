/**
 * Beebot - Discord Bot in C
 * 
 * Main entry point
 * 
 * This is a complete rewrite of the Java JDA Discord bot in pure C.
 * The bot maintains the same functionality and behavior as the original
 * but uses C libraries instead of Java dependencies.
 * 
 * Original Java bot features converted:
 * - Command system (151+ commands -> C function callbacks)
 * - Event handling (JDA listeners -> C callbacks)
 * - Configuration loading (JSON properties -> cJSON)
 * - Discord API interaction (JDA -> direct HTTP/WebSocket)
 * - Modular architecture (Java packages -> C modules)
 * 
 * Key differences from Java version:
 * - Manual memory management instead of garbage collection
 * - Function pointers instead of object-oriented command classes
 * - Explicit error handling instead of exceptions
 * - C string handling instead of Java String objects
 * 
 * Original authors:
 * @author NeutronSun (https://github.com/NeutronSun)
 * @author Leon412 (https://github.com/Leon412)
 * 
 * C conversion maintains the original bot's behavior and functionality.
 */

#include "beebot.h"
#include <signal.h>

/* Global bot reference for signal handling */
extern discord_client_t *g_bot;

/* Signal handler for graceful shutdown */
void signal_handler(int sig) {
    (void)sig;  /* Suppress unused warning */
    
    log_info("Received shutdown signal, stopping bot...");
    bot_stop();
}

void print_banner(void) {
    printf("\n");
    printf("██████╗ ███████╗███████╗██████╗  ██████╗ ████████╗\n");
    printf("██╔══██╗██╔════╝██╔════╝██╔══██╗██╔═══██╗╚══██╔══╝\n");
    printf("██████╔╝█████╗  █████╗  ██████╔╝██║   ██║   ██║   \n");
    printf("██╔══██╗██╔══╝  ██╔══╝  ██╔══██╗██║   ██║   ██║   \n");
    printf("██████╔╝███████╗███████╗██████╔╝╚██████╔╝   ██║   \n");
    printf("╚═════╝ ╚══════╝╚══════╝╚═════╝  ╚═════╝    ╚═╝   \n");
    printf("\n");
    printf("Discord Bot in C - Version 1.0\n");
    printf("Converted from Java JDA implementation\n");
    printf("Original authors: NeutronSun & Leon412\n");
    printf("\n");
}

void print_usage(const char *program_name) {
    printf("Usage: %s [options]\n", program_name);
    printf("\n");
    printf("Options:\n");
    printf("  -c, --config <file>    Specify config file (default: config.json)\n");
    printf("  -h, --help             Show this help message\n");
    printf("  -v, --version          Show version information\n");
    printf("  -d, --debug            Enable debug mode\n");
    printf("\n");
    printf("Examples:\n");
    printf("  %s                     Start bot with default config.json\n", program_name);
    printf("  %s -c mybot.json       Start bot with custom config\n", program_name);
    printf("  %s --debug             Start bot with debug logging\n", program_name);
    printf("\n");
}

int main(int argc, char *argv[]) {
    char *config_file = "config.json";
    int debug_override = 0;
    bot_config_t *config;
    
    /* Parse command line arguments */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            return 0;
        } else if (strcmp(argv[i], "-v") == 0 || strcmp(argv[i], "--version") == 0) {
            printf("Beebot 1.0 - Discord Bot in C\n");
            return 0;
        } else if (strcmp(argv[i], "-d") == 0 || strcmp(argv[i], "--debug") == 0) {
            debug_override = 1;
        } else if (strcmp(argv[i], "-c") == 0 || strcmp(argv[i], "--config") == 0) {
            if (i + 1 < argc) {
                config_file = argv[++i];
            } else {
                fprintf(stderr, "Error: --config requires a filename\n");
                return 1;
            }
        } else {
            fprintf(stderr, "Error: Unknown option %s\n", argv[i]);
            print_usage(argv[0]);
            return 1;
        }
    }
    
    print_banner();
    
    /* Setup signal handlers for graceful shutdown */
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    /* Load configuration */
    log_info("Loading configuration from %s", config_file);
    config = config_load(config_file);
    if (!config) {
        log_error("Failed to load configuration file: %s", config_file);
        log_error("Make sure the file exists and contains valid JSON with at least:");
        log_error("  {\"token\": \"YOUR_BOT_TOKEN_HERE\"}");
        return 1;
    }
    
    /* Apply debug override */
    if (debug_override) {
        config->debug_mode = 1;
        log_info("Debug mode enabled via command line");
    }
    
    /* Validate essential configuration */
    if (!config->token || strlen(config->token) == 0) {
        log_error("Bot token is missing or empty in configuration");
        log_error("Please set the 'token' field in %s", config_file);
        config_free(config);
        return 1;
    }
    
    /* Initialize bot */
    log_info("Initializing Beebot...");
    if (bot_init(config) != 0) {
        log_error("Failed to initialize bot");
        config_free(config);
        return 1;
    }
    
    /* Start bot */
    log_info("Starting bot...");
    if (bot_start() != 0) {
        log_error("Failed to start bot");
        bot_cleanup();
        config_free(config);
        return 1;
    }
    
    /* Main loop - wait for shutdown signal */
    log_info("Bot is running. Press Ctrl+C to stop.");
    while (bot_get_instance() && bot_get_instance()->running) {
        sleep(1);
    }
    
    /* Cleanup */
    log_info("Shutting down...");
    bot_cleanup();
    config_free(config);
    
    log_info("Beebot stopped successfully. Goodbye!");
    return 0;
}