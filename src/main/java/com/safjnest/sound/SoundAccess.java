package com.safjnest.sound;
import java.util.LinkedHashMap;
import java.util.Map;
import com.safjnest.auth.AuthException;
import com.safjnest.core.Bot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.http.HttpStatus;

public class SoundAccess {

    public record GuildAccess(String name, boolean administrator) {
    }

    public Map<String, GuildAccess> guilds(String userId) {
        long started = System.nanoTime();
        JDA jda = Bot.getJDA();
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED)
        throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", "Cannot verify guild membership");
        Map<String, GuildAccess> result = new LinkedHashMap<>();
        for (var guild : jda.getGuilds()) {
            try {
                Member member = guild.getMemberById(userId);
                if (member == null && !guild.isLoaded()) member = guild.retrieveMemberById(userId).complete();
                if (member != null) result.put(guild.getId(), new GuildAccess(guild.getName(), member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)));
            }
            catch (ErrorResponseException exception) {
                if (exception.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER)
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", "Cannot verify guild membership");
            }
            catch (RuntimeException exception) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", "Cannot verify guild membership");
            }
        }
        org.slf4j.LoggerFactory.getLogger(SoundAccess.class).debug("Sound memberships: {} guilds checked in {} ms", jda.getGuilds().size(), (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    public Map<String, Object> user(String id) {
        var user = Bot.getJDA() == null ? null : Bot.getJDA().getUserById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", user == null ? "Unknown" : user.getEffectiveName());
        result.put("avatarUrl", user == null ? null : user.getEffectiveAvatarUrl());
        return result;
    }
}
