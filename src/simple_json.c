/**
 * Simple JSON parser for Beebot configuration
 * 
 * A minimal JSON parser for loading bot configuration.
 * This replaces external JSON library dependencies.
 */

#include "beebot.h"

/* Simple JSON value structure */
typedef struct json_value {
    enum { JSON_STRING, JSON_NUMBER, JSON_BOOL } type;
    union {
        char *string_value;
        double number_value;
        int bool_value;
    } data;
} json_value_t;

/* Skip whitespace in JSON string */
static char* skip_whitespace(char *json) {
    while (*json && (*json == ' ' || *json == '\t' || *json == '\n' || *json == '\r')) {
        json++;
    }
    return json;
}

/* Parse a JSON string value */
static char* parse_string(char *json, char **result) {
    if (*json != '"') return NULL;
    json++; /* Skip opening quote */
    
    char *start = json;
    while (*json && *json != '"') {
        if (*json == '\\') json++; /* Skip escaped character */
        json++;
    }
    
    if (*json != '"') return NULL;
    
    size_t len = json - start;
    *result = malloc(len + 1);
    if (!*result) return NULL;
    
    strncpy(*result, start, len);
    (*result)[len] = '\0';
    
    return json + 1; /* Skip closing quote */
}

/* Parse a JSON number value */
static char* parse_number(char *json, double *result) {
    char *end;
    *result = strtod(json, &end);
    if (end == json) return NULL;
    return end;
}

/* Find a key in JSON object */
static char* find_key(char *json, const char *key) {
    json = skip_whitespace(json);
    if (*json != '{') return NULL;
    json++;
    
    while (*json) {
        json = skip_whitespace(json);
        if (*json == '}') break;
        
        /* Parse key */
        if (*json != '"') return NULL;
        json++;
        
        char *key_start = json;
        while (*json && *json != '"') json++;
        if (*json != '"') return NULL;
        
        size_t key_len = json - key_start;
        json++; /* Skip closing quote */
        
        json = skip_whitespace(json);
        if (*json != ':') return NULL;
        json++; /* Skip colon */
        json = skip_whitespace(json);
        
        /* Check if this is the key we want */
        if (strlen(key) == key_len && strncmp(key_start, key, key_len) == 0) {
            return json;
        }
        
        /* Skip value */
        if (*json == '"') {
            json++;
            while (*json && *json != '"') {
                if (*json == '\\') json++;
                json++;
            }
            if (*json == '"') json++;
        } else if (*json == '{') {
            int brace_count = 1;
            json++;
            while (*json && brace_count > 0) {
                if (*json == '{') brace_count++;
                else if (*json == '}') brace_count--;
                json++;
            }
        } else {
            /* Number, boolean, or null */
            while (*json && *json != ',' && *json != '}') json++;
        }
        
        json = skip_whitespace(json);
        if (*json == ',') json++;
    }
    
    return NULL;
}

/* Get string value for a key */
static char* get_string_value(char *json, const char *key) {
    char *value_pos = find_key(json, key);
    if (!value_pos) return NULL;
    
    char *result;
    if (parse_string(value_pos, &result)) {
        return result;
    }
    return NULL;
}

/* Get number value for a key */
static int get_number_value(char *json, const char *key, double *result) {
    char *value_pos = find_key(json, key);
    if (!value_pos) return 0;
    
    return parse_number(value_pos, result) != NULL;
}

bot_config_t* config_load(const char *filename) {
    FILE *file;
    long file_size;
    char *file_content;
    bot_config_t *config;
    
    /* Open and read file */
    file = fopen(filename, "r");
    if (!file) {
        log_error("Failed to open config file: %s", filename);
        return NULL;
    }
    
    /* Get file size */
    fseek(file, 0, SEEK_END);
    file_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    /* Allocate and read content */
    file_content = malloc(file_size + 1);
    if (!file_content) {
        log_error("Failed to allocate memory for config file");
        fclose(file);
        return NULL;
    }
    
    fread(file_content, 1, file_size, file);
    file_content[file_size] = '\0';
    fclose(file);
    
    /* Allocate config structure */
    config = malloc(sizeof(bot_config_t));
    if (!config) {
        log_error("Failed to allocate memory for config");
        free(file_content);
        return NULL;
    }
    
    memset(config, 0, sizeof(bot_config_t));
    
    /* Extract configuration values using simple JSON parser */
    config->token = get_string_value(file_content, "token");
    if (!config->token) {
        log_error("Missing or invalid token in config");
        config_free(config);
        free(file_content);
        return NULL;
    }
    
    config->prefix = get_string_value(file_content, "prefix");
    if (!config->prefix) {
        config->prefix = strdup("!");  /* Default prefix */
    }
    
    config->owner_id = get_string_value(file_content, "owner_id");
    
    double max_commands;
    if (get_number_value(file_content, "max_commands", &max_commands)) {
        config->max_commands = (int)max_commands;
    } else {
        config->max_commands = 1000;  /* Default */
    }
    
    double debug_mode;
    if (get_number_value(file_content, "debug_mode", &debug_mode)) {
        config->debug_mode = (int)debug_mode;
    } else {
        config->debug_mode = 0;  /* Default */
    }
    
    free(file_content);
    
    log_info("Configuration loaded successfully from %s", filename);
    return config;
}

void config_free(bot_config_t *config) {
    if (!config) return;
    
    if (config->token) free(config->token);
    if (config->prefix) free(config->prefix);
    if (config->owner_id) free(config->owner_id);
    
    free(config);
}