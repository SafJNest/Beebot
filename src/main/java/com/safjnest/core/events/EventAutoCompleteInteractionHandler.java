package com.safjnest.core.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.lol.AugmentData;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.twitch.TwitchClient;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.TrackData;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.model.guild.alert.AlertData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import com.safjnest.core.cache.managers.GuilddataCache;

public class EventAutoCompleteInteractionHandler extends ListenerAdapter {
    private boolean isFocused;
    private String value;

    private final int MAX_CHOICES = 25;

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {        
        ArrayList<Choice> choices = new ArrayList<>();
        String name = e.getName();

        this.isFocused = !e.getFocusedOption().getValue().isEmpty();
        this.value = e.getFocusedOption().getValue();

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

        else if (e.getFocusedOption().getName().equals("voice"))
            name = "tts";

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
            case "tts":
                choices = tts(e);
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

        QueryCollection sounds = null;
        if (isFocused) sounds = DatabaseHandler.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), value);
        else sounds = DatabaseHandler.getUserGuildSounds(e.getUser().getId(), e.getGuild().getId()).shuffle().limit(MAX_CHOICES);

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("id") + ": " + sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("id")));
        }
        return choices;

    }


    private ArrayList<Choice> userSound(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        QueryCollection sounds = null;
        
        if (isFocused) sounds = DatabaseHandler.getFocusedUserSound(e.getUser().getId(), value);
        else sounds = DatabaseHandler.getUserSound(e.getUser().getId()).shuffle().limit(MAX_CHOICES);

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
        List<String> champions = Arrays.asList(LeagueHandler.getChampions());

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
        List<AugmentData> augments = LeagueHandler.getAugments();

        if (isFocused) {
            int max = 0;
            for (int i = 0; i < augments.size() && max < MAX_CHOICES; i++) {
                String augmentName = augments.get(i).getName().toLowerCase();
                String augmentId = augments.get(i).getId();

                if (augmentName.startsWith(value.toLowerCase()) || augmentId.startsWith(value)){
                    choices.add(new Choice(augments.get(i).getId() + " . " + augments.get(i).getName(), augments.get(i).getId()));
                    max++;
                }
            }
        } else {
            Collections.shuffle(augments);
            for (int i = 0; i < MAX_CHOICES; i++)
                choices.add(new Choice(augments.get(i).getId() + " . " + augments.get(i).getName(), augments.get(i).getId()));
        }

        return choices;
    }

    private ArrayList<Choice> soundboard(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        
        QueryCollection soundboards = null;
        if (!isFocused) soundboards = DatabaseHandler.getRandomSoundboard(e.getGuild().getId(), e.getUser().getId());
        else soundboards = DatabaseHandler.getFocusedSoundboard(e.getGuild().getId(), e.getUser().getId(), value);

        for (QueryRecord soundboard : soundboards) {
            String server_name = Bot.getJDA().getGuildById(soundboard.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(soundboard.get("guild_id")).getName();
            String label = soundboard.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, soundboard.get("id")));
        }

        return choices;
    }


    private ArrayList<Choice> soundboardSound(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        if (e.getOption("name") == null)
            return null;
        
        String soundboardId = e.getOption("name").getAsString();

        QueryCollection sounds = null;

        if (isFocused) sounds = DatabaseHandler.getFocusedSoundFromSounboard(soundboardId, value);
        else sounds = DatabaseHandler.getSoundsFromSoundBoard(soundboardId);

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("sound_id")));
        }

        return choices;

    }

    private ArrayList<Choice> greet(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();


        QueryCollection sounds = null;
        if (isFocused) sounds = DatabaseHandler.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), e.getFocusedOption().getValue());
        else sounds = DatabaseHandler.getlistGuildSounds(e.getGuild().getId(), 25);

        for (QueryRecord sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("name") + " (" + sound.get("id") + ") - " + server_name;
            choices.add(new Choice(label, sound.get("id")));
        }

        return choices;
    }


    private ArrayList<Choice> tts(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        List<String[]> voices = TTSVoices.getVoiceArray();

        if (isFocused) {
            for (int i = 0; i < 25; i++) {
                String[] voice = voices.get(i);
                if ((voice[0] + " " + voice[1]).toLowerCase().contains(value.toLowerCase()))
                    choices.add(new Choice(voice[0] + " - " + voice[1], voice[1]));
            }
        }
        else {
            Collections.shuffle(voices);
            for (int i = 0; i < 25; i++)
                choices.add(new Choice(voices.get(i)[0] + " - " + voices.get(i)[1], voices.get(i)[1]));
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
        GuildData guildData = GuilddataCache.getGuild(e.getGuild().getId());

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
        GuildData guildData = GuilddataCache.getGuild(e.getGuild().getId());

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
        GuildData guildData = GuilddataCache.getGuild(e.getGuild().getId());

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

        HashMap<String, String> accounts = Bot.getUserData(e.getUser().getId()).getRiotAccounts();
        R4J r4j = LeagueHandler.getRiotApi();

        if (accounts == null || accounts.isEmpty()) {
            return choices;
        }
        
        HashMap<String, String> accountNames = new HashMap<>();
        for (String k : accounts.keySet()) {
            String account_id = k;

            LeagueShard shard = LeagueShard.values()[Integer.valueOf(accounts.get(account_id))];
            Summoner summoner = LeagueHandler.getSummonerByAccountId(account_id, shard);
            RiotAccount riotAccount = r4j.getAccountAPI().getAccountByPUUID(shard.toRegionShard(), summoner.getPUUID());
            accountNames.put(account_id, riotAccount.getName() + "#" + riotAccount.getTag());
        }

        ArrayList<Choice> personal = new ArrayList<>();
        
        if (isFocused) {
            accountNames.forEach((k, v) -> {
                if (v.toLowerCase().contains(value.toLowerCase())) personal.add(new Choice(v, k));
            });
        }
        else accountNames.forEach((k, v) -> personal.add(new Choice(v, k)));

        return personal;

    }

    private ArrayList<Choice> summoner(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        QueryCollection summoners = new QueryCollection();
        LeagueShard shard = e.getOption("region") != null ? LeagueHandler.getShardFromOrdinal(Integer.valueOf(e.getOption("region").getAsString())) : GuilddataCache.getGuild(e.getGuild().getId()).getLeagueShard(e.getChannelIdLong());
        
        if (!isFocused) {
            HashMap<String, String> accounts = Bot.getUserData(e.getUser().getId()).getRiotAccounts();
            R4J r4j = LeagueHandler.getRiotApi();
    
            if (accounts == null || accounts.isEmpty()) {
                return choices;
            }
            
            HashMap<String, String> accountNames = new HashMap<>();
            for (String k : accounts.keySet()) {
                String account_id = k;
    
                shard = LeagueShard.values()[Integer.valueOf(accounts.get(account_id))];
                Summoner summoner = LeagueHandler.getSummonerByAccountId(account_id, shard);
                RiotAccount riotAccount = r4j.getAccountAPI().getAccountByPUUID(shard.toRegionShard(), summoner.getPUUID());
                accountNames.put(riotAccount.getName() + "#" + riotAccount.getTag(), riotAccount.getName() + "#" + riotAccount.getTag());
            }
        
            accountNames.forEach((k, v) -> choices.add(new Choice(v, k)));
            return choices;
        }
        
        summoners = DatabaseHandler.getFocusedSummoners(value, shard);
        

        for (QueryRecord summoner : summoners) {
            choices.add(new Choice(summoner.get("riot_id"), summoner.get("riot_id")));
        }

        return choices;

    }

    private ArrayList<Choice> streamer(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        List<String> subs = new ArrayList<>();
        for (TwitchData twitch : GuilddataCache.getGuild(e.getGuild()).getTwitchDatas().values()) {
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
        for (Item item : LeagueHandler.getRiotApi().getDDragonAPI().getItems().values()) {
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

        QueryCollection playlists = DatabaseHandler.getPlaylistsWithSize(e.getUser().getId());
        

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
            playlists.shuffle();

            int i = 0;
            for (QueryRecord playlist : playlists) {
                String label = playlist.get("name") + " (" + playlist.get("size") + " songs)";
                if (i < MAX_CHOICES) choices.add(new Choice(label, playlist.get("id")));
            }
        }
          

        return choices;
    }

    private ArrayList<Choice> playlistSong(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        if (e.getOption("playlist-name") == null)
            return choices;
        
        int playlistId = e.getOption("playlist-name").getAsInt();

        QueryCollection songs = DatabaseHandler.getPlaylistTracks(playlistId, null, null);
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

