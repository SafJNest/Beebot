package com.safjnest.spring.controller.beebot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.bot.Bot;
import com.safjnest.bot.audio.tts.TTSVoices;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.GuildData.Settings;
import com.safjnest.bot.model.guild.GuildData.AlertState;
import com.safjnest.bot.model.guild.GuildData.Error;
import com.safjnest.bot.model.guild.GuildData.Result;
import com.safjnest.bot.model.guild.BlacklistData;
import com.safjnest.bot.model.guild.ChannelData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertSendType;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.status.log.BotLogger;
import com.safjnest.utils.twitch.TwitchClient;

import jakarta.servlet.http.HttpServletResponse;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beebot")
public class GuildController {

    private static final Set<String> SINGLE_ALERTS = Set.of("welcome", "leave", "boost", "levelUp");

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_ROLES = 25;
    private static final int MAX_PREFIX_LENGTH = 32;
    private static final int MAX_THRESHOLD = 127;
    private static final int MAX_REWARD_LEVEL = 100;

    private final GuildCache cache;

    private final Function<String, Guild> discordGuilds;
    private final BiConsumer<AuthContext, String> guildAccess;
    private final Function<AuthContext, List<Server>> guildDirectory;

    private final Consumer<String> registerTwitch;
    private final Consumer<String> unregisterTwitch;
    private final Function<String, TwitchClient.Streamer> twitchLookup;

    @Autowired
    public GuildController(Environment environment) {
        this(GuildCache.getInstance(), id -> Bot.getJDA() == null ? null : Bot.getJDA().getGuildById(id),
            new DiscordAuthService(environment.getProperty("DISCORD_CLIENT_ID"), environment.getProperty("DISCORD_CLIENT_SECRET"),
                environment.getProperty("AUTH_ENCRYPTION_KEY")),
            TwitchClient::registerSubEvent);
    }

    private GuildController(GuildCache cache, Function<String, Guild> discordGuilds,
            DiscordAuthService auth, Consumer<String> registerTwitch) {
        this(cache, discordGuilds, auth::requireAdministrator, auth::servers, registerTwitch);
    }

    public GuildController(GuildCache cache, Function<String, Guild> discordGuilds,
            BiConsumer<AuthContext, String> guildAccess, Function<AuthContext, List<Server>> guildDirectory,
            Consumer<String> registerTwitch) {
        this(cache, discordGuilds, guildAccess, guildDirectory, registerTwitch,
            TwitchClient::unregisterIfUnused, TwitchClient::lookupStreamer);
    }

    public GuildController(GuildCache cache, Function<String, Guild> discordGuilds,
            BiConsumer<AuthContext, String> guildAccess, Function<AuthContext, List<Server>> guildDirectory,
            Consumer<String> registerTwitch, Consumer<String> unregisterTwitch, Function<String, TwitchClient.Streamer> twitchLookup) {
        this.cache = cache;

        this.discordGuilds = discordGuilds;
        this.guildAccess = guildAccess;
        this.guildDirectory = guildDirectory;

        this.registerTwitch = registerTwitch;
        this.unregisterTwitch = unregisterTwitch;
        this.twitchLookup = twitchLookup;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping({"/guilds", "/servers"})
    public List<Server> servers(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        return guildDirectory.apply(context);
    }

    @GetMapping("/guilds/{guildId}")
    public Settings getGuild(@PathVariable("guildId") String guildId,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        requireAdministrator(context, guildId);

        Guild guild = requireGuild(guildId);
        Map<String, Object> settings = withGuild(guildId, GuildData::snapshot);

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
            entry.put("color", role.getColors().getPrimary());
            entry.put("position", role.getPosition());
            entry.put("managed", role.isManaged());
            entry.put("assignable", canManageRoles && !role.isManaged() && !role.isPublicRole() && self.canInteract(role));
            roles.add(entry);
        }

        return new Settings(guildId, guild.getName(), guild.getIconUrl(), discordChannels, roles, settings);
    }

    @PostMapping("/guilds/{guildId}/settings")
    public Map<String, Object> updateSettings(@PathVariable("guildId") String guildId,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) JsonNode body) {
        requireAdministrator(context, guildId);

        Guild guild = requireGuild(guildId);

        if (body == null || !body.isObject() || body.isEmpty())
            throw new Exception(Type.INVALID_REQUEST, "invalid_request", "A non-empty JSON object is required");

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

    @DeleteMapping("/guilds/{guildId}/alerts/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSingleAlert(@PathVariable("guildId") String guildId, @PathVariable("type") String type,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        authorizeSettings(context, guildId);
        if (!SINGLE_ALERTS.contains(type))
            throw new Exception(Type.NOT_FOUND, "alert_not_found", "Unknown alert type");

        deleteAlert(guildId, type(type), null);
    }

    @DeleteMapping("/guilds/{guildId}/alerts/reward/{level}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReward(@PathVariable("guildId") String guildId, @PathVariable("level") String level,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        authorizeSettings(context, guildId);
        if (!isRewardLevel(level))
            throw Exception.invalidRequest("Reward level must be between 1 and 100");

        deleteAlert(guildId, AlertType.REWARD, level);
    }

    @DeleteMapping("/guilds/{guildId}/alerts/twitch/{streamerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTwitch(@PathVariable("guildId") String guildId, @PathVariable("streamerId") String streamerId,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        authorizeSettings(context, guildId);
        if (!isStreamerId(streamerId))
            throw Exception.invalidRequest("Invalid streamer ID");

        boolean deleted = deleteAlert(guildId, AlertType.TWITCH, streamerId);
        if (deleted)
            unregisterTwitch.accept(streamerId);
    }

    @DeleteMapping("/guilds/{guildId}/settings/channels/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannelSettings(@PathVariable("guildId") String guildId, @PathVariable("channelId") String channelId,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        authorizeSettings(context, guildId);
        if (!isGuildId(channelId))
            throw Exception.invalidRequest("Invalid channel ID");

        if (!withGuild(guildId, guild -> guild.deleteChannelData(channelId)))
            throw settingsUnavailable();
    }

    @GetMapping("/guilds/{guildId}/alerts/twitch/lookup")
    public TwitchClient.Streamer lookupTwitch(@PathVariable("guildId") String guildId, @RequestParam("login") String login,
            @RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        authorizeSettings(context, guildId);

        return twitchLookup.apply(login);
    }

    // ================================================================================================================

    private void authorizeSettings(AuthContext context, String guildId) {
        requireAdministrator(context, guildId);
        requireGuild(guildId);
    }

    private boolean deleteAlert(String guildId, AlertType type, String key) {
        return withGuild(guildId, guild -> {
            AlertData alert = guild.findAlert(type, key);
            if (alert == null)
                return false;
            if (!guild.deleteAlert(alert))
                throw settingsUnavailable();

            return true;
        });
    }

    private Exception settingsUnavailable() {
        return new Exception(Type.UNAVAILABLE, "settings_unavailable", "Guild settings storage is unavailable");
    }

    private void requireAdministrator(AuthContext context, String guildId) {
        if (context == null || context.user() == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "A user session is required");

        if (!isGuildId(guildId))
            throw new Exception(Type.INVALID_REQUEST, "invalid_guild_id", "Invalid guild ID");

        guildAccess.accept(context, guildId);
    }

    private boolean isGuildId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,19}"))
            return false;

        try {
            return Long.parseUnsignedLong(id) != 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private Guild requireGuild(String id) {
        Guild guild = discordGuilds.apply(id);

        if (guild == null)
            throw new Exception(Type.NOT_FOUND, "guild_not_found", "The bot is not present in this guild");

        return guild;
    }

    private <T> T withGuild(String id, Function<GuildData, T> operation) {
        try {
            return cache.withGuildData(id, operation);
        } catch (IllegalStateException exception) {
            throw settingsUnavailable();
        }
    }

    private void refreshAlerts(String guildId, Map<String, Object> results) {
        boolean hasAlerts = false;

        for (String key : results.keySet()) {
            if (SINGLE_ALERTS.contains(key) || key.equals("twitch") || key.equals("reward"))
                hasAlerts = true;
        }

        if (!hasAlerts)
            return;

        withGuild(guildId, guild -> {
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String key = entry.getKey();

                if (entry.getValue() instanceof Result result && SINGLE_ALERTS.contains(key))
                    entry.setValue(new Result(
                        result.status(),
                        result.errors(),
                        AlertState.from(guild.getAlert(type(key)), guild.isExperienceEnabled())
                    ));

                if ((key.equals("twitch") || key.equals("reward")) && entry.getValue() instanceof Map<?, ?> collection) {
                    Map<String, Object> refreshed = new LinkedHashMap<>();

                    for (Map.Entry<?, ?> item : collection.entrySet()) {
                        Result result = (Result) item.getValue();
                        String id = (String) item.getKey();
                        AlertData alert = guild.findAlert(key.equals("twitch") ? AlertType.TWITCH : AlertType.REWARD, id);

                        refreshed.put(id, new Result(
                            result.status(),
                            result.errors(),
                            AlertState.from(alert, guild.isExperienceEnabled())
                        ));
                    }

                    entry.setValue(refreshed);
                }
            }

            return null;
        });
    }

    private Object updateEntries(Guild guild, String block, JsonNode value) {
        if (!value.isObject())
            return failure(block, "invalid_type", "Deve essere un oggetto indicizzato per chiave", null);

        Map<String, Object> results = new LinkedHashMap<>();

        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            String key = entry.getKey();

            if (block.equals("reward") && !isRewardLevel(key)) {
                results.put(key, failure(key, "invalid_level", "Il livello deve essere compreso tra 1 e 100", null));
                continue;
            }

            if (!block.equals("reward") && !isStreamerId(key)) {
                results.put(key, failure(key, "invalid_id", "Identificativo non valido", null));
                continue;
            }

            results.put(key, block.equals("channels")
                ? updateBlock(guild, block, key, entry.getValue())
                : updateAlert(guild, block.equals("twitch") ? AlertType.TWITCH : AlertType.REWARD, key, entry.getValue()));
        }

        return results;
    }

    private boolean isRewardLevel(String level) {
        return level.matches("[1-9][0-9]{0,2}") && Integer.parseInt(level) <= MAX_REWARD_LEVEL;
    }

    private boolean isStreamerId(String id) {
        return id.matches("[1-9][0-9]{0,31}");
    }

    private Result updateAlert(Guild discord, AlertType type, String key, JsonNode body) {
        Result result = withGuild(discord.getId(), guild -> {
            AlertData current = guild.findAlert(type, key);
            AlertState previous = AlertState.from(current, guild.isExperienceEnabled());

            AlertData edit = current == null ? guild.newAlert(type, key) : current.deferEdit();
            List<Error> errors = new ArrayList<>();

            parseAlert(discord, type, edit, body, errors);

            if (!errors.isEmpty())
                return new Result("error", List.copyOf(errors), previous);

            if (!edit.update())
                return failure(null, "storage_error", "Impossibile salvare il blocco", previous);

            if (current == null) {
                guild.addAlert(edit);
                current = edit;
            }

            return new Result("ok", List.of(), AlertState.from(current, guild.isExperienceEnabled()));
        });

        if (type != AlertType.TWITCH || !result.status().equals("ok"))
            return result;

        try {
            registerTwitch.accept(key);
        } catch (RuntimeException exception) {
            BotLogger.error("[GUILD SETTINGS] Failed to register Twitch subscription for guild " + discord.getId());

            return new Result("saved_with_errors", List.of(new Error(null, "twitch_subscription_failed",
                "Configurazione salvata, registrazione Twitch non riuscita; riprovare")), result.alert());
        }

        return result;
    }

    private Result updateBlock(Guild discord, String block, String channelId, JsonNode body) {
        boolean channelExists = channelId == null || hasChannel(discord, channelId, false);

        return withGuild(discord.getId(), guild -> {
            List<Error> errors = new ArrayList<>();
            boolean saved;

            switch (block) {
                case "general" -> {
                    GuildData edit = guild.deferEdit();
                    parseGeneral(edit, body, errors);

                    saved = errors.isEmpty() && edit.update();
                }
                case "blacklist" -> {
                    BlacklistData edit = guild.getBlacklistData().deferEdit();
                    parseBlacklist(discord, edit, body, errors);

                    saved = errors.isEmpty() && edit.update();
                }
                default -> {
                    ChannelData channel = channelExists ? guild.getChannelData(channelId) : new ChannelData(channelId, discord.getId());
                    ChannelData edit = channel.deferEdit();

                    parseChannel(edit, body, errors);

                    if (!channelExists)
                        errors.add(new Error("channelId", "invalid_channel", "Canale non presente nella guild"));

                    saved = errors.isEmpty() && edit.update();
                }
            }

            if (!errors.isEmpty())
                return new Result("error", List.copyOf(errors), null);

            return saved ? new Result("ok", List.of(), null)
                : failure(null, "storage_error", "Impossibile salvare il blocco", null);
        });
    }

    private void parseAlert(Guild guild, AlertType type, AlertData edit, JsonNode body, List<Error> errors) {
        if (!object(body, errors))
            return;

        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();

            switch (field) {
                case "message", "privateMessage" -> {
                    if (value.isNull()) {
                        if (field.equals("message"))
                            edit.setMessage("");
                        else
                            edit.setPrivateMessage(null);
                    } else if (value.isTextual() && value.textValue().length() <= MAX_MESSAGE_LENGTH) {
                        if (field.equals("message"))
                            edit.setMessage(value.textValue());
                        else
                            edit.setPrivateMessage(value.textValue());
                    } else {
                        errors.add(new Error(field, "invalid_message", "Deve essere una stringa di massimo 2000 caratteri o null"));
                    }
                }
                case "enabled" -> bool(field, value, errors, edit::setEnabled);
                case "sendType" -> {
                    AlertSendType sendType = null;

                    if (value.isTextual()) {
                        try {
                            sendType = AlertSendType.valueOf(value.textValue());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (sendType == null)
                        errors.add(new Error(field, "invalid_send_type", "Usare CHANNEL, PRIVATE o BOTH"));
                    else
                        edit.setSendType(sendType);
                }
                case "channelId" -> {
                    if (type == AlertType.LEVEL_UP || type == AlertType.REWARD)
                        errors.add(new Error(field, "unsupported_field", "Questo alert non ha un canale fisso"));
                    else
                        edit.setAlertChannel(channel(guild, value, errors));
                }
                case "roleIds" -> {
                    if (type == AlertType.LEAVE || type == AlertType.TWITCH)
                        errors.add(new Error(field, "unsupported_field", "Questo alert non supporta roleIds"));
                    else
                        edit.setRoles(roles(guild, value, errors));
                }
                case "roleId" -> {
                    if (type != AlertType.TWITCH)
                        errors.add(new Error(field, "unsupported_field", "Campo riservato agli alert Twitch"));
                    else if (value.isNull())
                        ((TwitchData) edit).setStreamerRole(null);
                    else if (value.isTextual() && hasRole(guild, value.textValue()))
                        ((TwitchData) edit).setStreamerRole(value.textValue());
                    else
                        errors.add(new Error(field, "invalid_role", "Ruolo non presente nella guild"));
                }
                case "temporary" -> {
                    if (type != AlertType.REWARD)
                        errors.add(new Error(field, "unsupported_field", "Campo riservato ai reward"));
                    else
                        bool(field, value, errors, ((RewardData) edit)::setTemporary);
                }
                default -> errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
            }
        }
    }

    private void parseGeneral(GuildData edit, JsonNode body, List<Error> errors) {
        if (!object(body, errors))
            return;

        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();

            switch (field) {
                case "experienceEnabled" -> bool(field, value, errors, edit::setExpSystem);
                case "prefix" -> {
                    if (value.isTextual() && !value.textValue().isEmpty() && value.textValue().length() <= MAX_PREFIX_LENGTH)
                        edit.setPrefix(value.textValue());
                    else
                        errors.add(new Error(field, "invalid_prefix", "Deve contenere da 1 a 32 caratteri"));
                }
                case "voice" -> {
                    if (!value.isTextual() || TTSVoices.getLanguage(value.textValue()) == null)
                        errors.add(new Error(field, "invalid_voice", "Voce non riconosciuta"));
                    else
                        edit.setVoice(value.textValue());
                }
                case "leagueShard" -> {
                    LeagueShard shard = shard(value, false, errors);

                    if (shard != null)
                        edit.setLeagueShard(shard);
                }
                default -> errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
            }
        }
    }

    private void parseBlacklist(Guild guild, BlacklistData edit, JsonNode body, List<Error> errors) {
        if (!object(body, errors))
            return;

        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();

            switch (field) {
                case "enabled" -> bool(field, value, errors, edit::setBlacklistEnabled);
                case "channelId" -> edit.setBlackChannelId(channel(guild, value, errors));
                case "threshold" -> {
                    if (value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0 && value.intValue() <= MAX_THRESHOLD)
                        edit.setThreshold(value.intValue());
                    else
                        errors.add(new Error(field, "invalid_threshold", "Deve essere un intero tra 0 e 127"));
                }
                default -> errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
            }
        }
    }

    private void parseChannel(ChannelData edit, JsonNode body, List<Error> errors) {
        if (!object(body, errors))
            return;

        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();

            switch (field) {
                case "experienceEnabled" -> bool(field, value, errors, edit::enableExperience);
                case "statisticsEnabled" -> bool(field, value, errors, edit::setCommand);
                case "experienceModifier" -> {
                    if (value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0)
                        edit.setExperienceModifier(value.doubleValue());
                    else
                        errors.add(new Error(field, "invalid_modifier", "Deve essere un numero finito maggiore o uguale a zero"));
                }
                case "leagueShard" -> edit.setLeagueShard(shard(value, true, errors));
                default -> errors.add(new Error(field, "unknown_field", "Campo non riconosciuto"));
            }
        }
    }

    private boolean object(JsonNode body, List<Error> errors) {
        if (body.isObject())
            return true;

        errors.add(new Error(null, "invalid_type", "Il blocco deve essere un oggetto"));

        return false;
    }

    private void bool(String field, JsonNode value, List<Error> errors, Consumer<Boolean> setter) {
        if (value.isBoolean())
            setter.accept(value.booleanValue());
        else
            errors.add(new Error(field, "invalid_boolean", "Deve essere true o false"));
    }

    private String channel(Guild guild, JsonNode value, List<Error> errors) {
        if (value.isNull())
            return null;

        if (value.isTextual() && hasChannel(guild, value.textValue(), true))
            return value.textValue();

        errors.add(new Error("channelId", "invalid_channel", "Canale testuale non presente nella guild"));

        return null;
    }

    private List<String> roles(Guild guild, JsonNode value, List<Error> errors) {
        if (!value.isArray() || value.size() > MAX_ROLES) {
            errors.add(new Error("roleIds", "invalid_roles", "Deve essere un array di massimo 25 ID ruolo"));

            return List.of();
        }

        Set<String> roles = new LinkedHashSet<>();

        for (JsonNode role : value) {
            if (!role.isTextual() || !hasRole(guild, role.textValue()))
                errors.add(new Error("roleIds", "invalid_role", "Ruolo non presente nella guild"));
            else
                roles.add(role.textValue());
        }

        return new ArrayList<>(roles);
    }

    private LeagueShard shard(JsonNode value, boolean channel, List<Error> errors) {
        if (value.isNull() && channel)
            return null;

        if (value.isTextual()) {
            try {
                LeagueShard shard = LeagueShard.valueOf(value.textValue());

                if (!channel || shard.ordinal() != 0)
                    return shard;
            } catch (IllegalArgumentException ignored) {
            }
        }

        errors.add(new Error("leagueShard", "invalid_shard", "Shard non valido; null eredita lo shard della guild"));

        return null;
    }

    private boolean hasChannel(Guild guild, String id, boolean textOnly) {
        for (GuildChannel channel : guild.getChannels()) {
            if (channel.getId().equals(id) && (!textOnly || channel.getType() == ChannelType.TEXT))
                return true;
        }

        return false;
    }

    private boolean hasRole(Guild guild, String id) {
        for (Role role : guild.getRoles()) {
            if (role.getId().equals(id))
                return true;
        }

        return false;
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
}
