package com.safjnest.guild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.auth.AuthException;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.AutomatedAction;
import com.safjnest.model.guild.GuildSettings;
import com.safjnest.model.guild.GuildSettings.AlertState;
import com.safjnest.model.guild.GuildSettings.Error;
import com.safjnest.model.guild.GuildSettings.Result;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.GuildSettingsDB;
import com.safjnest.sql.database.GuildSettingsDB.Snapshot;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.utils.twitch.TwitchClient;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import org.springframework.http.HttpStatus;

public class GuildService {

    private static final List<String> SINGLE_ALERTS = List.of("welcome", "leave", "boost", "levelUp");
    private static final Set<String> ALERT_BLOCKS = Set.of("welcome", "leave", "boost", "levelUp", "twitch", "reward");
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_ROLES = 25;
    private static final int MAX_PREFIX_LENGTH = 32;
    private static final int MAX_THRESHOLD = 127;
    private static final int MAX_REWARD_LEVEL = 100;

    private final GuildSettingsDB database;
    private final Function<String, Guild> guilds;
    private final Consumer<String> invalidate;
    private final Consumer<String> registerTwitch;

    public GuildService() {
        this(new GuildSettingsDB(), id -> Bot.getJDA() == null ? null : Bot.getJDA().getGuildById(id),
            id -> GuildCache.getInstance().invalidate(id), TwitchClient::registerSubEvent);
    }

    public GuildService(GuildSettingsDB database, Function<String, Guild> guilds,
            Consumer<String> invalidate, Consumer<String> registerTwitch) {
        this.database = database;
        this.guilds = guilds;
        this.invalidate = invalidate;
        this.registerTwitch = registerTwitch;
    }

    public GuildSettings getGuild(String guildId) {
        Guild guild = requireGuild(guildId);
        Snapshot snapshot = load(guildId);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("general", general(snapshot.guild()));
        settings.put("blacklist", blacklist(snapshot.guild()));
        settings.put("channels", channels(snapshot.channels()));
        for (String key : SINGLE_ALERTS)
            settings.put(key, state(snapshot, type(key), null));

        Map<String, AlertState> twitch = new LinkedHashMap<>();
        Map<String, AlertState> reward = new LinkedHashMap<>();
        for (AlertData alert : snapshot.alerts()) {
            if (alert instanceof TwitchData entry)
                twitch.put(entry.getStreamer(), AlertState.from(entry, experienceEnabled(snapshot)));
            if (alert instanceof RewardData entry)
                reward.put(String.valueOf(entry.getLevel()), AlertState.from(entry, experienceEnabled(snapshot)));
        }
        settings.put("twitch", twitch);
        settings.put("reward", reward);
        List<AutomatedAction> actions = new ArrayList<>();
        for (QueryRecord row : snapshot.actions()) actions.add(new AutomatedAction(row));
        settings.put("warning", Map.of("actions", actions));

        List<Map<String, Object>> discordChannels = new ArrayList<>();
        for (GuildChannel channel : guild.getChannels()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", channel.getId());
            entry.put("name", channel.getName());
            entry.put("type", channel.getType().name());
            entry.put("parentId", channel instanceof ICategorizableChannel categorized ? categorized.getParentCategoryId() : null);
            entry.put("position", channel instanceof IPositionableChannel positioned ? positioned.getPositionRaw() : null);
            discordChannels.add(entry);
        }
        List<Map<String, Object>> roles = new ArrayList<>();
        Member self = guild.getSelfMember();
        boolean canManageRoles = self.hasPermission(Permission.MANAGE_ROLES);
        for (Role role : guild.getRoles()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", role.getId());
            entry.put("name", role.getName());
            entry.put("color", role.getColorRaw());
            entry.put("position", role.getPosition());
            entry.put("managed", role.isManaged());
            entry.put("assignable", canManageRoles && !role.isManaged() && !role.isPublicRole() && self.canInteract(role));
            roles.add(entry);
        }
        return new GuildSettings(guildId, guild.getName(), guild.getIconUrl(), discordChannels, roles, settings);
    }

    public Map<String, Object> updateSettings(String guildId, JsonNode body) {
        Guild guild = requireGuild(guildId);
        if (body == null || !body.isObject() || body.isEmpty())
            throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", "A non-empty JSON object is required");

        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            Object result = switch (key) {
                case "welcome", "leave", "boost", "levelUp" -> updateAlert(guild, type(key), null, value);
                case "twitch", "reward", "channels" -> updateEntries(guild, key, value);
                case "general", "blacklist" -> updateBlock(guild, key, null, value);
                default -> failure(key, "unknown_setting", "Blocco non modificabile o non riconosciuto", null);
            };
            results.put(key, result);
        }
        refreshAlerts(guildId, results);
        return results;
    }

    public static boolean isGuildId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,19}")) return false;
        try { return Long.parseUnsignedLong(id) != 0; }
        catch (NumberFormatException exception) { return false; }
    }

    // ================================================================================================================

    private void refreshAlerts(String guildId, Map<String, Object> results) {
        boolean hasAlerts = false;
        for (String key : results.keySet()) {
            if (ALERT_BLOCKS.contains(key)) hasAlerts = true;
        }
        if (!hasAlerts) return;
        Snapshot snapshot = load(guildId);
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() instanceof Result result && SINGLE_ALERTS.contains(key))
                entry.setValue(new Result(result.status(), result.errors(), state(snapshot, type(key), null)));
            if ((key.equals("twitch") || key.equals("reward")) && entry.getValue() instanceof Map<?, ?> collection) {
                Map<String, Object> refreshed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> item : collection.entrySet()) {
                    Result result = (Result) item.getValue();
                    String id = (String) item.getKey();
                    refreshed.put(id, new Result(result.status(), result.errors(),
                        state(snapshot, key.equals("twitch") ? AlertType.TWITCH : AlertType.REWARD, id)));
                }
                entry.setValue(refreshed);
            }
        }
    }

    private Object updateEntries(Guild guild, String block, JsonNode value) {
        if (!value.isObject()) return failure(block, "invalid_type", "Deve essere un oggetto indicizzato per chiave", null);
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            String key = entry.getKey();
            if (block.equals("reward") && (!key.matches("[1-9][0-9]{0,2}") || Integer.parseInt(key) > MAX_REWARD_LEVEL)) {
                results.put(key, failure(key, "invalid_level", "Il livello deve essere compreso tra 1 e 100", null));
                continue;
            }
            if (!block.equals("reward") && !key.matches("[1-9][0-9]{0,31}")) {
                results.put(key, failure(key, "invalid_id", "Identificativo non valido", null));
                continue;
            }
            results.put(key, block.equals("channels")
                ? updateBlock(guild, block, key, entry.getValue())
                : updateAlert(guild, block.equals("twitch") ? AlertType.TWITCH : AlertType.REWARD, key, entry.getValue()));
        }
        return results;
    }

    private Result updateAlert(Guild guild, AlertType type, String key, JsonNode value) {
        Snapshot snapshot = load(guild.getId());
        AlertState current = state(snapshot, type, key);
        List<Error> errors = new ArrayList<>();
        Map<String, Object> values = parseAlert(guild, type, value, errors);
        if (!errors.isEmpty()) return new Result("error", errors, current);

        AlertData saved;
        try {
            saved = database.saveAlert(guild.getId(), type, key, values);
        } catch (IllegalStateException exception) {
            BotLogger.error("[GUILD SETTINGS] Failed to save alert for guild " + guild.getId());
            return failure(null, "storage_error", "Impossibile salvare il blocco", current);
        }
        invalidate.accept(guild.getId());
        List<Error> sideEffects = new ArrayList<>();
        if (type == AlertType.TWITCH) {
            try { registerTwitch.accept(key); }
            catch (RuntimeException exception) {
                BotLogger.error("[GUILD SETTINGS] Failed to register Twitch subscription for guild " + guild.getId());
                sideEffects.add(new Error(null, "twitch_subscription_failed", "Configurazione salvata, registrazione Twitch non riuscita; riprovare"));
            }
        }
        return new Result(sideEffects.isEmpty() ? "ok" : "saved_with_errors", sideEffects,
            AlertState.from(saved, experienceEnabled(snapshot)));
    }

    private Result updateBlock(Guild guild, String block, String channelId, JsonNode value) {
        List<Error> errors = new ArrayList<>();
        Map<String, Object> values = parseBlock(guild, block, value, errors);
        if (channelId != null && !hasChannel(guild, channelId, false))
            errors.add(new Error("channelId", "invalid_channel", "Canale non presente nella guild"));
        if (!errors.isEmpty()) return new Result("error", errors, null);
        try {
            database.saveSettings(guild.getId(), block, channelId, values);
        } catch (IllegalStateException exception) {
            BotLogger.error("[GUILD SETTINGS] Failed to save settings for guild " + guild.getId());
            return failure(null, "storage_error", "Impossibile salvare il blocco", null);
        }
        invalidate.accept(guild.getId());
        return new Result("ok", List.of(), null);
    }

    private Map<String, Object> parseAlert(Guild guild, AlertType type, JsonNode body, List<Error> errors) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!body.isObject()) {
            errors.add(new Error(null, "invalid_type", "Il blocco deve essere un oggetto"));
            return values;
        }
        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            switch (field) {
                case "message", "privateMessage" -> {
                    if (value.isNull()) values.put(field, field.equals("message") ? "" : null);
                    else if (value.isTextual() && value.textValue().length() <= MAX_MESSAGE_LENGTH) values.put(field, value.textValue());
                    else errors.add(new Error(field, "invalid_message", "Deve essere una stringa di massimo 2000 caratteri o null"));
                }
                case "enabled" -> bool(values, field, value, errors);
                case "sendType" -> {
                    AlertSendType sendType = null;
                    if (value.isTextual()) {
                        try { sendType = AlertSendType.valueOf(value.textValue()); }
                        catch (IllegalArgumentException ignore) { }
                    }
                    if (sendType == null) errors.add(new Error(field, "invalid_send_type", "Usare CHANNEL, PRIVATE o BOTH"));
                    else values.put(field, sendType.ordinal());
                }
                case "channelId" -> {
                    if (type == AlertType.LEVEL_UP || type == AlertType.REWARD)
                        errors.add(new Error(field, "unsupported_field", "Questo alert non ha un canale fisso"));
                    else channel(values, guild, field, value, errors);
                }
                case "roleIds" -> {
                    if (type == AlertType.LEAVE || type == AlertType.TWITCH)
                        errors.add(new Error(field, "unsupported_field", "Questo alert non supporta roleIds"));
                    else roles(values, guild, value, errors);
                }
                case "roleId" -> {
                    if (type != AlertType.TWITCH) errors.add(new Error(field, "unsupported_field", "Campo riservato agli alert Twitch"));
                    else if (value.isNull()) values.put(field, null);
                    else if (value.isTextual() && hasRole(guild, value.textValue())) values.put(field, value.textValue());
                    else errors.add(new Error(field, "invalid_role", "Ruolo non presente nella guild"));
                }
                case "temporary" -> {
                    if (type != AlertType.REWARD) errors.add(new Error(field, "unsupported_field", "Campo riservato ai reward"));
                    else bool(values, field, value, errors);
                }
                default -> errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
            }
        }
        return values;
    }

    private Map<String, Object> parseBlock(Guild guild, String block, JsonNode body, List<Error> errors) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!body.isObject()) {
            errors.add(new Error(null, "invalid_type", "Il blocco deve essere un oggetto"));
            return values;
        }
        Set<String> allowed = switch (block) {
            case "general" -> Set.of("prefix", "experienceEnabled", "voice", "leagueShard");
            case "blacklist" -> Set.of("enabled", "threshold", "channelId");
            default -> Set.of("experienceEnabled", "experienceModifier", "statisticsEnabled", "leagueShard");
        };
        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (!allowed.contains(field)) {
                errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
                continue;
            }
            switch (field) {
                case "enabled", "experienceEnabled", "statisticsEnabled" -> bool(values, field, value, errors);
                case "channelId" -> channel(values, guild, field, value, errors);
                case "prefix" -> {
                    if (value.isTextual() && !value.textValue().isEmpty() && value.textValue().length() <= MAX_PREFIX_LENGTH)
                        values.put(field, value.textValue());
                    else errors.add(new Error(field, "invalid_prefix", "Deve contenere da 1 a 32 caratteri"));
                }
                case "voice" -> {
                    String language = value.isTextual() ? TTSVoices.getLanguage(value.textValue()) : null;
                    if (language == null) errors.add(new Error(field, "invalid_voice", "Voce non riconosciuta"));
                    else {
                        values.put(field, value.textValue());
                        values.put("language", language);
                    }
                }
                case "leagueShard" -> {
                    if (value.isNull() && block.equals("channels")) values.put(field, 0);
                    else {
                        LeagueShard shard = null;
                        if (value.isTextual()) {
                            try { shard = LeagueShard.valueOf(value.textValue()); }
                            catch (IllegalArgumentException ignore) { }
                        }
                        if (shard == null || block.equals("channels") && shard.ordinal() == 0)
                            errors.add(new Error(field, "invalid_shard", "Shard non valido; null eredita lo shard della guild"));
                        else values.put(field, shard.ordinal());
                    }
                }
                case "threshold" -> {
                    if (value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0 && value.intValue() <= MAX_THRESHOLD)
                        values.put(field, value.intValue());
                    else errors.add(new Error(field, "invalid_threshold", "Deve essere un intero tra 0 e 127"));
                }
                case "experienceModifier" -> {
                    if (value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0)
                        values.put(field, value.doubleValue());
                    else errors.add(new Error(field, "invalid_modifier", "Deve essere un numero finito maggiore o uguale a zero"));
                }
            }
        }
        return values;
    }

    private void bool(Map<String, Object> values, String field, JsonNode value, List<Error> errors) {
        if (value.isBoolean()) values.put(field, value.booleanValue());
        else errors.add(new Error(field, "invalid_boolean", "Deve essere true o false"));
    }

    private void channel(Map<String, Object> values, Guild guild, String field, JsonNode value, List<Error> errors) {
        if (value.isNull()) values.put(field, null);
        else if (value.isTextual() && hasChannel(guild, value.textValue(), true)) values.put(field, value.textValue());
        else errors.add(new Error(field, "invalid_channel", "Canale testuale non presente nella guild"));
    }

    private void roles(Map<String, Object> values, Guild guild, JsonNode value, List<Error> errors) {
        if (!value.isArray() || value.size() > MAX_ROLES) {
            errors.add(new Error("roleIds", "invalid_roles", "Deve essere un array di massimo 25 ID ruolo"));
            return;
        }
        Set<String> roles = new LinkedHashSet<>();
        for (JsonNode role : value) {
            if (!role.isTextual() || !hasRole(guild, role.textValue()))
                errors.add(new Error("roleIds", "invalid_role", "Ruolo non presente nella guild"));
            else roles.add(role.textValue());
        }
        values.put("roleIds", new ArrayList<>(roles));
    }

    private boolean hasChannel(Guild guild, String id, boolean textOnly) {
        for (GuildChannel channel : guild.getChannels()) {
            if (channel.getId().equals(id) && (!textOnly || channel.getType() == ChannelType.TEXT)) return true;
        }
        return false;
    }

    private boolean hasRole(Guild guild, String id) {
        for (Role role : guild.getRoles()) {
            if (role.getId().equals(id)) return true;
        }
        return false;
    }

    private Guild requireGuild(String guildId) {
        if (!isGuildId(guildId))
            throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_guild_id", "Invalid guild ID");
        Guild guild = guilds.apply(guildId);
        if (guild == null)
            throw new AuthException(HttpStatus.NOT_FOUND, "guild_not_found", "The bot is not present in this guild");
        return guild;
    }

    private Snapshot load(String guildId) {
        try { return database.load(guildId); }
        catch (IllegalStateException exception) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "settings_unavailable", "Guild settings storage is unavailable");
        }
    }

    private boolean experienceEnabled(Snapshot snapshot) {
        return snapshot.guild().isEmpty() || snapshot.guild().getAsBoolean("exp_enabled");
    }

    private AlertState state(Snapshot snapshot, AlertType type, String key) {
        return AlertState.from(GuildSettingsDB.find(snapshot.alerts(), type, key), experienceEnabled(snapshot));
    }

    private AlertType type(String key) {
        return switch (key) {
            case "welcome" -> AlertType.WELCOME;
            case "leave" -> AlertType.LEAVE;
            case "boost" -> AlertType.BOOST;
            case "levelUp" -> AlertType.LEVEL_UP;
            default -> throw new IllegalArgumentException("Unknown alert type");
        };
    }

    private Result failure(String field, String code, String message, AlertState alert) {
        return new Result("error", List.of(new Error(field, code, message)), alert);
    }

    private Map<String, Object> general(QueryRecord row) {
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("prefix", row.isEmpty() ? database.getDefaultPrefix() : row.get("prefix"));
        general.put("experienceEnabled", row.isEmpty() || row.getAsBoolean("exp_enabled"));
        general.put("voice", row.get("name_tts") == null ? "John" : row.get("name_tts"));
        general.put("language", row.get("language_tts") == null ? "en-us" : row.get("language_tts"));
        general.put("leagueShard", row.isEmpty() ? LeagueShard.EUW1.name() : LeagueShard.values()[row.getAsInt("league_shard")].name());
        return general;
    }

    private Map<String, Object> blacklist(QueryRecord row) {
        Map<String, Object> blacklist = new LinkedHashMap<>();
        blacklist.put("enabled", !row.isEmpty() && row.getAsBoolean("blacklist_enabled"));
        blacklist.put("threshold", row.getAsInt("threshold"));
        blacklist.put("channelId", row.get("blacklist_channel"));
        return blacklist;
    }

    private Map<String, Object> channels(List<QueryRecord> rows) {
        Map<String, Object> channels = new LinkedHashMap<>();
        for (QueryRecord row : rows) {
            Map<String, Object> channel = new LinkedHashMap<>();
            channel.put("experienceEnabled", row.getAsBoolean("exp_enabled"));
            channel.put("experienceModifier", row.getAsDouble("exp_modifier"));
            channel.put("statisticsEnabled", row.getAsBoolean("stats_enabled"));
            channel.put("leagueShard", row.getAsInt("league_shard") == 0 ? null : LeagueShard.values()[row.getAsInt("league_shard")].name());
            channels.put(row.get("channel_id"), channel);
        }
        return channels;
    }
}
