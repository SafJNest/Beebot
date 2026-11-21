package com.safjnest.bot.events;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.PermissionHandler;
import com.safjnest.utils.twitch.TwitchClient;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.bot.audio.TrackData;
import com.safjnest.bot.audio.tts.TTSVoices;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.bot.Bot;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertKey;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.database.nosql.MongoDB;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Augment;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.service.StaticDataService;
import com.safjnest.lol.utils.ChampionUtils;

public class EventAutoCompleteInteractionHandler extends ListenerAdapter {
    private static final int MAX_CHOICES = 25;

    private boolean isFocused;
    private String value;

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
        String query = e.getFocusedOption().getValue();
        if (e.getFocusedOption().getName().equals("voice")) {
            e.replyChoices(voiceChoices(query)).queue();
            return;
        }

        ArrayList<Choice> choices = new ArrayList<>();
        String name = e.getName();

        this.isFocused = !query.isEmpty();
        this.value = query;

        if (e.getFullCommandName().equals("soundboard create") 
            || e.getFocusedOption().getName().startsWith("sound-"))
            name = "play";

        else if (e.getFocusedOption().getName().equals("sound_add"))
            name = "play";

        else if (e.getFocusedOption().getName().equals("sound_remove"))
            name = "sound_remove";

        else if (e.getFullCommandName().equals("soundboard play")
                || e.getFocusedOption().getName().equals("soundboard_name")
                || e.getFullCommandName().equals("soundboard remove")
                || e.getFullCommandName().equals("soundboard delete"))
            name = "soundboard_select";

        else if (e.getFocusedOption().getName().equals("user_sound"))
            name = "user_sound";

        else if (e.getFullCommandName().equals("bugsnotifier"))
            name = "help";

        else if (e.getFocusedOption().getName().equals("role_remove"))
            name = "alert_role";

        else if (e.getFocusedOption().getName().equals("reward_level"))
            name = "rewards_level";

        else if (e.getFocusedOption().getName().equals("reward_roles"))
            name = "reward_roles";
        
        else if (e.getFocusedOption().getName().equals("personal_summoner"))
            name = "personal_summoner";

        else if (e.getFocusedOption().getName().equals("champion"))
            name = "champion";

        else if (e.getFocusedOption().getName().equals("streamer"))
            name = "streamer_name";
        
        else if (e.getFocusedOption().getName().equals("item"))
            name = "item";
        
        else if (e.getFocusedOption().getName().equals("playlist-name"))
            name = "playlist";

        else if (e.getFocusedOption().getName().equals("playlist-song"))
            name = "playlist_song";

        else if (e.getFocusedOption().getName().equals("summoner"))
            name = "summoner";
        
        else if (e.getFocusedOption().getName().equals("playlist-order"))
            name = "playlist_order";

        else if (e.getFocusedOption().getName().equals("custom-build"))
            name = "custom_build";

        
        switch (name) {
            case "play":
                choices = playSound(e);

                break;
            case "user_sound":
                choices = userSound(e);

                break;
            case "help":
                choices = help(e);
                break;

            case "champion":
                choices = champion(e);
                break;

            case "augment":
                choices = augment(e);
                break;

            case "soundboard_select":
                choices = soundboard(e);
                break;
            case "sound_remove":
                choices = soundboardSound(e);
                break;

            case "greet":
                choices = greet(e);
                break;
            case "jumpto":
                choices = jump(e);
                break;
            case "alert_role":
                choices = alert(e);
                break;
            case "rewards_level":
                choices = rewardsLevel(e);
                break;
            case "reward_roles":
                choices = rewardRole(e);
                break;
            case "personal_summoner":
                choices = personalSummoner(e);
                break;
            case "streamer_name":
                choices = streamer(e);
                break;
            case "item":
                choices = item(e);
                break;
            case "playlist":
                choices = playlist(e);
                break;
            case "playlist_song":
            case "playlist_order":
                choices = playlistSong(e);
                break;
            case "summoner":
                choices = summoner(e);
                break;
        }

        e.replyChoices(choices).queue();
    }

    /**
     * Sound upload by the user and present in the current guild
     * @param e
     * @return
     */
    private ArrayList<Choice> playSound(CommandAutoCompleteInteractionEvent e) {

        ArrayList<Choice> choices = new ArrayList<>();

        List<QueryRecord> sounds = null;
        if (isFocused) sounds = BotDB.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), value);
        else {
            sounds = new ArrayList<>(BotDB.getUserGuildSounds(e.getUser().getId(), e.getGuild().getId()));
            Collections.shuffle(sounds);
            if (sounds.size() > MAX_CHOICES) sounds = new ArrayList<>(sounds.subList(0, MAX_CHOICES));
        }

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("id") + ": " + sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("id")));
        }
        return choices;

    }


    private ArrayList<Choice> userSound(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        List<QueryRecord> sounds = null;
        
        if (isFocused) sounds = BotDB.getFocusedUserSound(e.getUser().getId(), value);
        else {
            sounds = new ArrayList<>(BotDB.getUserSound(e.getUser().getId()));
            Collections.shuffle(sounds);
            if (sounds.size() > MAX_CHOICES) sounds = new ArrayList<>(sounds.subList(0, MAX_CHOICES));
        }

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("id") + ": " + sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("id")));
        }

        return choices;
    } 


    private ArrayList<Choice> help(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        
        List<String> commands = CommandsLoader.getAllCommandsNames(e.getUser().getId());
        
        if (isFocused) {
            int i = 0;
            for (String command : commands) {
                if (command.toLowerCase().contains(value.toLowerCase())) {
                    choices.add(new Choice(command, command));
                    i++;
                }
                if (i >= MAX_CHOICES) break;
            }   
        }
        else {
            Collections.shuffle(commands);
            for (int i = 0; i < MAX_CHOICES; i++)
                choices.add(new Choice(commands.get(i), commands.get(i)));
        }

        return choices;    
    }

    
    private ArrayList<Choice> champion(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        List<String> champions = ChampionUtils.getChampionsNames();
        
        if (isFocused) {
            int max = 0;
            for (int i = 0; i < champions.size() && max < MAX_CHOICES; i++) {
                if (champions.get(i).toLowerCase().startsWith(value.toLowerCase())) {
                    choices.add(new Choice(champions.get(i), champions.get(i)));
                    max++;
                }
            }
        } else {
            Collections.shuffle(champions);
            for (int i = 0; i < MAX_CHOICES; i++)
                choices.add(new Choice(champions.get(i), champions.get(i)));
        }

        return choices;
    }


    private ArrayList<Choice> augment(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        List<Augment> augments = LeagueHandler.getAugments();

        if (isFocused) {
            int max = 0;
            for (int i = 0; i < augments.size() && max < MAX_CHOICES; i++) {
                String augmentName = augments.get(i).name().toLowerCase();
                String augmentId = augments.get(i).id();

                if (augmentName.startsWith(value.toLowerCase()) || augmentId.startsWith(value)){
                    choices.add(new Choice(augments.get(i).id() + " . " + augments.get(i).name(), augments.get(i).id()));
                    max++;
                }
            }
        } else {
            Collections.shuffle(augments);
            for (int i = 0; i < MAX_CHOICES; i++)
                choices.add(new Choice(augments.get(i).id() + " . " + augments.get(i).name(), augments.get(i).id()));
        }

        return choices;
    }

    private ArrayList<Choice> soundboard(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        
        boolean edit = !"soundboard play".equals(e.getFullCommandName());
        boolean administrator = e.getMember().hasPermission(Permission.ADMINISTRATOR);
        List<QueryRecord> boards = BotDB.get().discordSoundboards(e.getGuild().getId(), e.getUser().getId(), edit, administrator, value);
        for (QueryRecord board : boards) {
            String guildId = board.get("guild_id");
            Guild guild = guildId == null ? null : e.getJDA().getGuildById(guildId);
            String label = guild == null ? board.get("name") : board.get("name") + " (" + guild.getName() + ")";
            choices.add(new Choice(PermissionHandler.ellipsis(label, 100), board.get("id")));
        }

        return choices;
    }


    private ArrayList<Choice> soundboardSound(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        if (e.getOption("name") == null)
            return null;
        
        String soundboardId = e.getOption("name").getAsString();

        if (!soundboardId.matches("[1-9][0-9]*"))
            return choices;
        try {
            BotDB.get().soundboardEditAccess(soundboardId, e.getUser().getId(), e.getGuild().getId(),
                e.getMember().hasPermission(Permission.ADMINISTRATOR));
        } catch (com.safjnest.exception.Exception failure) {
            return choices;
        }

        String viewer = e.getUser().getId();
        String guildId = e.getGuild().getId();
        BotDB database = BotDB.get();
        for (QueryRecord sound : database.readSoundboardSounds(soundboardId)) {
            String name = sound.get("name");
            if (!BotDB.isSoundVisible(sound, viewer, java.util.Set.of(guildId))
                    || isFocused && !name.regionMatches(true, 0, value, 0, value.length()))
                continue;

            String sourceId = sound.get("guild_id");
            Guild source = sourceId == null ? null : e.getJDA().getGuildById(sourceId);
            String label = name + " (" + (source == null ? "Unknown" : source.getName()) + ")";
            choices.add(new Choice(PermissionHandler.ellipsis(label, 100), sound.get("id")));
        }

        return choices;

    }

    private ArrayList<Choice> greet(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();


        List<QueryRecord> sounds = null;
        if (isFocused) sounds = BotDB.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), e.getFocusedOption().getValue());
        else sounds = BotDB.getlistGuildSounds(e.getGuild().getId(), 25);

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("name") + " (" + sound.get("id") + ") - " + server_name;
            choices.add(new Choice(label, sound.get("id")));
        }

        return choices;
    }


    static ArrayList<Choice> voiceChoices(String input) {
        String query = input.strip().toLowerCase(Locale.ROOT);
        ArrayList<Choice> choices = new ArrayList<>();
        List<String[]> voices = TTSVoices.getVoiceArray();

        if (query.isEmpty())
            Collections.shuffle(voices);

        for (String[] voice : voices) {
            String searchable = (voice[0] + " " + voice[1]).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query))
                continue;

            choices.add(new Choice(voice[0] + " - " + voice[1], voice[1]));
            if (choices.size() == MAX_CHOICES)
                break;
        }

        return choices;
    }


    private ArrayList<Choice> jump(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        
        List<AudioTrack> queue = PlayerManager.get().getGuildMusicManager(e.getGuild()).getTrackScheduler().getQueue();

        if (isFocused) {
            for (int i = 0, max = 0; i < queue.size() && max < MAX_CHOICES; i++) {
                String title = "[" + (i + 1) + "] " + queue.get(i).getInfo().title;
                if (title.toLowerCase().contains(value.toLowerCase())) {
                    choices.add(new Choice(title, String.valueOf(i + 1)));
                    max++;
                }
            }
        }
        else {
            for (int i = 0; i < queue.size() && i < MAX_CHOICES; i++)
                choices.add(new Choice("[" + (i + 1) + "] " + queue.get(i).getInfo().title, String.valueOf(i + 1)));
        }

        return choices;
    }


    private ArrayList<Choice> alert(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>(); 
        GuildData guildData = GuildCache.getGuildOrPut(e.getGuild().getId());

        AlertData alert = guildData.getAlert(AlertType.WELCOME);

        if (alert == null || alert.getRoles() == null)
            return choices;

        HashMap<Integer, String> alertRoles = alert.getRoles();  

        List<Role> roles = new ArrayList<>();
        for (Role r : e.getGuild().getRoles()) {
            if (alertRoles.containsValue(r.getId()))
                roles.add(r);
        }

        Collections.shuffle(roles);
        if (isFocused) {
            for (Role role : roles) {
                if (role.getName().toLowerCase().contains(value.toLowerCase()))
                    choices.add(new Choice(role.getName(), role.getId()));
            }
        }
        else {
            for (Role role : roles) 
                choices.add(new Choice(role.getName(), role.getId()));
        }
        return choices;
    }


    private ArrayList<Choice> rewardsLevel(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>(); 
        GuildData guildData = GuildCache.getGuildOrPut(e.getGuild().getId());

        HashMap<AlertKey<?>, AlertData> alerts = guildData.getAlerts();
        List<String> levels = new ArrayList<>();
        
        for (AlertData data : alerts.values()) {
            if (data.getType() == AlertType.REWARD)
                levels.add(String.valueOf(((RewardData) data).getLevel()));
        }


        if (isFocused) {
            for (String level : levels) {
                if (level.startsWith(value))
                    choices.add(new Choice(level, level));
            }
        } else {
            Collections.shuffle(levels);
            for (int i = 0; i < levels.size() && i < MAX_CHOICES; i++)
                choices.add(new Choice(levels.get(i), levels.get(i)));
        }

        return choices;
    
    }


    private ArrayList<Choice> rewardRole(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>(); 
        GuildData guildData = GuildCache.getGuildOrPut(e.getGuild().getId());

        if (e.getOption("reward_level") == null) return choices;;

        String rewardLevel = e.getOption("reward_level").getAsString();
        RewardData reward = guildData.getAlert(AlertType.REWARD, Integer.parseInt(rewardLevel));
        if (reward == null || reward.getRoles() == null) return choices;
        
        HashMap<Integer, String> rewardRoles = reward.getRoles();
        List<Role> roles = new ArrayList<>();
        for (Role r : e.getGuild().getRoles()) {
            if (rewardRoles.containsValue(r.getId()))
                roles.add(r);
        }

        Collections.shuffle(roles);
        if (isFocused) {
            for (Role role : roles) {
                if (role.getName().toLowerCase().contains(value.toLowerCase()))
                    choices.add(new Choice(role.getName(), role.getId()));
            }
        }
        else {
            for (int i = 0; i < roles.size() && i < MAX_CHOICES; i++)
            choices.add(new Choice(roles.get(i).getName(), roles.get(i).getId())); 
        }

        return choices;
    }


    private ArrayList<Choice> personalSummoner(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        List<QueryRecord> accounts = MongoDB.findAccountsByUserId(e.getUser().getId());
        if (accounts.isEmpty()) return choices;

        ArrayList<Choice> personal = new ArrayList<>();
        for (QueryRecord account : accounts) {
            String puuid = account.getAsString("puuid");
            if (puuid == null) puuid = account.getAsString("_id");
            String riotId = account.getAsString("riotId");
            if (puuid == null || riotId == null || riotId.isBlank()) continue;
            if (!isFocused || riotId.toLowerCase().contains(value.toLowerCase())) personal.add(new Choice(riotId, puuid));
        }

        return personal;

    }

    private ArrayList<Choice> summoner(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        LeagueShard defaultShard = e.isFromGuild() ? GuildCache.getGuildOrPut(e.getGuild().getId()).getLeagueShard(e.getChannelId()) : LeagueShard.EUW1;
        LeagueShard shard = e.getOption("region") != null ? LeagueShard.valueOf(e.getOption("region").getAsString().toUpperCase()) : defaultShard;
        
        if (!isFocused) return personalSummoner(e);
        
        choices = new ArrayList<>(SummonerService.autocomplete(value, shard));
        
        return choices;

    }

    private ArrayList<Choice> streamer(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        List<String> subs = new ArrayList<>();
        for (TwitchData twitch : GuildCache.getGuildOrPut(e.getGuild()).getTwitchDatas().values()) {
            subs.add(twitch.getStreamer());
        }
        List<com.github.twitch4j.helix.domain.User> users = TwitchClient.getStreamersById(subs);

        if (isFocused) {
            for (com.github.twitch4j.helix.domain.User user : users) {
                if (user.getDisplayName().toLowerCase().contains(value.toLowerCase()))
                    choices.add(new Choice(user.getDisplayName(), user.getId()));
            }
        }
        else {
            for (com.github.twitch4j.helix.domain.User user : users)
                choices.add(new Choice(user.getDisplayName(), user.getLogin()));
        }


        return choices;

    }

    private ArrayList<Choice> item(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        HashMap<String, String> items = new HashMap<>();
        for (Item item : StaticDataService.getItems().values()) {
            // 30 is arena, so the item is different with the same name (riot?)         
            if (item.getMaps().get("30")) items.put(item.getName().replaceAll("<.+?>", "") + " (ARENA)", item.getId() + "");
            else if (item.getMaps().get("33")) items.put(item.getName().replaceAll("<.+?>", "") + " (SWARM)", item.getId() + "");
            else items.put(item.getName().replaceAll("<.+?>", ""), item.getId() + ""); 
        }
        

        if (isFocused) {
            List<String> keys = new ArrayList<>(items.keySet());
            int i = 0;
            for (String key : keys) {
                if (key.toLowerCase().contains(value.toLowerCase()) && i < MAX_CHOICES) {
                    choices.add(new Choice(key, items.get(key)));
                    i++;
                }
            }
        }
        else {
            List<String> keys = new ArrayList<>(items.keySet());
            Collections.shuffle(keys);
            for (int i = 0; i < keys.size() && i < MAX_CHOICES; i++)
                choices.add(new Choice(keys.get(i), items.get(keys.get(i))));
        }
          

        return choices;
    }

        private ArrayList<Choice> playlist(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        List<QueryRecord> playlists = BotDB.getPlaylistsWithSize(e.getUser().getId());
        

        if (isFocused) {
            int i = 0;
            for (QueryRecord playlist : playlists) {
                if (playlist.get("name").toLowerCase().contains(value.toLowerCase()) && i < MAX_CHOICES) {
                    String label = playlist.get("name") + " (" + playlist.get("size") + " songs)";
                    choices.add(new Choice(label, playlist.get("id")));
                    i++;
                }
                    
            }
        }
        else {
            Collections.shuffle(playlists);

            int i = 0;
            for (QueryRecord playlist : playlists) {
                String label = playlist.get("name") + " (" + playlist.get("size") + " songs)";
                if (i < MAX_CHOICES) choices.add(new Choice(label, playlist.get("id")));
                i++;
            }
        }
          

        return choices;
    }

    private ArrayList<Choice> playlistSong(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        if (e.getOption("playlist-name") == null)
            return choices;
        
        int playlistId = e.getOption("playlist-name").getAsInt();

        List<QueryRecord> songs = BotDB.getPlaylistTracks(playlistId, null, null);
        List<AudioTrack> queue = new ArrayList<>();
        for (QueryRecord song : songs) {
            AudioTrack track = PlayerManager.get().decodeTrack(song.get("encoded_track"));
            if (track != null) {
                TrackData data = new TrackData(AudioType.SOUND);
            
                data.setPlaylistSongId(song.getAsInt("id"));
                data.setPlaylistSongOrder(song.getAsInt("order") + 1);
            
                track.setUserData(data);
                queue.add(track);
            } 
        }

        if (isFocused) {
            int i = 0;
            for (AudioTrack track : queue) {
                String label = "[" + track.getUserData(TrackData.class).getPlaylistSongOrder() + "] " + track.getInfo().title;
                if (label.toLowerCase().contains(value.toLowerCase()) && i < MAX_CHOICES) {
                    choices.add(new Choice(PermissionHandler.ellipsis(label, 100), track.getUserData(TrackData.class).getPlaylistSongId()));
                    i++;
                }
            }
        }
        else {
            Collections.shuffle(queue);
            for (int i = 0; i < queue.size() && i < MAX_CHOICES; i++) {
                String label = "[" + queue.get(i).getUserData(TrackData.class).getPlaylistSongOrder() + "] " + queue.get(i).getInfo().title;
                choices.add(new Choice(PermissionHandler.ellipsis(label, 100), queue.get(i).getUserData(TrackData.class).getPlaylistSongId()));
            }
        }

        return choices;

    }
}
