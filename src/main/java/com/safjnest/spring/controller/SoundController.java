package com.safjnest.spring.controller;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.model.sound.Sound;
import com.safjnest.sound.SoundPeriod;
import com.safjnest.spring.config.BeebotAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/beebot")

public class SoundController {

    private final SoundCache sounds;
    @Autowired

    public SoundController(Environment environment) {
        this(SoundCache.getInstance());
        sounds.setCdnUrl(environment.getProperty("CDN_URL", ""));
    }

    public SoundController(SoundCache sounds) {
        this.sounds=sounds;
    }
    @ModelAttribute public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/guilds/{guildId}/sounds")
    public Map<String,Object> guildSounds(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("guildId") String guildId,@RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        var context=context(auth);
        return sounds.guildSounds(context,guildId,period(context,period,timezone,null,null,null));
    }
    @GetMapping("/sounds/me")
    public Map<String,Object> mine(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        var context=context(auth);
        return sounds.mySounds(context,period(context,period,timezone,null,null,null));
    }
    @GetMapping("/sounds/{soundId}")
    public Sound sound(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId) {
        return sounds.sound(context(auth),soundId);
    }
    @GetMapping("/sounds")
    public Map<String,Object> search(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="q", required=false) String q,@RequestParam(name="tag", required=false) String tag,
            @RequestParam(name="sort", defaultValue="plays") String sort,@RequestParam(name="order", defaultValue="desc") String order,@RequestParam(name="limit", defaultValue="100") int limit) {
        return sounds.search(context(auth),q,tag,sort,order,limit);
    }
    @GetMapping("/tags")
    public Map<String,Object> tags(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@RequestParam(name="q", required=false) String q) {
        return sounds.searchTags(context(auth),q);
    }
    @GetMapping("/guilds/{guildId}/sounds/analytics")
    public Map<String,Object> guildAnalytics(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("guildId") String guildId,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        var context=context(auth);
        return sounds.guildAnalytics(context,guildId,period(context,period,timezone,null,null,null));
    }
    @GetMapping("/sounds/me/analytics")
    public Map<String,Object> myAnalytics(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        var context=context(auth);
        return sounds.myAnalytics(context,period(context,period,timezone,null,null,null));
    }
    @GetMapping("/sounds/{soundId}/analytics")
    public Map<String,Object> analytics(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,
            @RequestParam(name="guildId", required=false) String guildId,@RequestParam(name="period", defaultValue="30d") String period,
            @RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone,@RequestParam(name="cursor", required=false) String cursor) {
        var context=context(auth);
        return sounds.soundAnalytics(context,soundId,guildId,period(context,period,timezone,cursor,soundId,guildId));
    }
    @PatchMapping("/sounds/{soundId}")
    public Sound patch(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        return sounds.patch(context(auth),soundId,body);
    }
    @DeleteMapping("/sounds/{soundId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId) {
        sounds.delete(context(auth),soundId);
    }
    @PatchMapping("/sounds/{soundId}/like")
    public Map<String,Object> like(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        return sounds.vote(context(auth),soundId,1,body);
    }
    @PatchMapping("/sounds/{soundId}/dislike")
    public Map<String,Object> dislike(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        return sounds.vote(context(auth),soundId,-1,body);
    }
    @PostMapping("/me/settings")
    public Map<String,Object> settings(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext auth,@RequestBody(required=false) JsonNode body) {
        return sounds.settings(context(auth),body);
    }
    // ================================================================================================================

    private SoundCache.Context context(AuthContext auth) {
        if(auth==null||auth.user()==null)throw new AuthException(HttpStatus.UNAUTHORIZED,"invalid_session","User session required");
        return sounds.context(auth.user().id());
    }

    private SoundPeriod period(SoundCache.Context context,String period,String zone,String cursor,String sound,String guild) {
        return SoundPeriod.of(period,zone,cursor,sound,context.userId(),guild);
    }
}
