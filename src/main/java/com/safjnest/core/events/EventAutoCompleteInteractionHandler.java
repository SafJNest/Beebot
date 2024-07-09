package com.safjnest.core.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.AugmentData;
import com.safjnest.util.LOL.RiotHandler;
import com.safjnest.util.Twitch.TwitchClient;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.tts.TTSVoices;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.AlertData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class EventAutoCompleteInteractionHandler extends ListenerAdapter {
    private boolean isFocused;
    private String value;

    private final int MAX_CHOICES = 25;

    /**
     * Sound upload by the user and present in the current guild
     * @param e
     * @return
     */
    private ArrayList<Choice> playSound(CommandAutoCompleteInteractionEvent e) {

        ArrayList<Choice> choices = new ArrayList<>();

        QueryResult sounds = null;
        if (isFocused) sounds = DatabaseHandler.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), value);
        else sounds = DatabaseHandler.getUserGuildSounds(e.getUser().getId(), e.getGuild().getId()).shuffle().limit(MAX_CHOICES);

        for (ResultRow sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("id") + ": " + sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("id")));
        }
        return choices;

    }


    private ArrayList<Choice> userSound(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        QueryResult sounds = null;
        
        if (isFocused) sounds = DatabaseHandler.getFocusedUserSound(e.getUser().getId(), value);
        else sounds = DatabaseHandler.getUserSound(e.getUser().getId()).shuffle().limit(MAX_CHOICES);

        for (ResultRow sound : sounds) {
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
        List<String> champions = Arrays.asList(RiotHandler.getChampions());

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
        List<AugmentData> augments = RiotHandler.getAugments();

        if (isFocused) {
            int max = 0;
            for (int i = 0; i < augments.size() && max < MAX_CHOICES; i++) {
                String augmentName = augments.get(i).getName().toLowerCase();
                String augmentId = augments.get(i).getId();

                if (augmentName.startsWith(value.toLowerCase()) || augmentId.startsWith(value)){
                    choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                    max++;
                }
            }
        } else {
            Collections.shuffle(augments);
            for (int i = 0; i < MAX_CHOICES; i++)
                choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
        }

        return choices;
    }

    private ArrayList<Choice> soundboard(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        
        QueryResult soundboards = null;
        if (!isFocused) soundboards = DatabaseHandler.getRandomSoundboard(e.getGuild().getId(), e.getUser().getId());
        else soundboards = DatabaseHandler.getFocusedSoundboard(e.getGuild().getId(), e.getUser().getId(), value);

        for (ResultRow soundboard : soundboards) {
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

        QueryResult sounds = null;

        if (isFocused) sounds = DatabaseHandler.getFocusedSoundFromSounboard(soundboardId, value);
        else sounds = DatabaseHandler.getSoundsFromSoundBoard(soundboardId);

        for (ResultRow sound : sounds) {
            String server_name = Bot.getJDA().getGuildById(sound.get("guild_id")) == null ? "Unknown" : Bot.getJDA().getGuildById(sound.get("guild_id")).getName();
            String label = sound.get("name") + " (" + server_name + ")";
            choices.add(new Choice(label, sound.get("sound_id")));
        }

        return choices;

    }

    private ArrayList<Choice> greet(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();


        QueryResult sounds = null;
        if (isFocused) sounds = DatabaseHandler.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), e.getFocusedOption().getValue());
        else sounds = DatabaseHandler.getlistGuildSounds(e.getGuild().getId(), 25);

        for (ResultRow sound : sounds) {
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
        GuildData guildData = Bot.getGuildData(e.getGuild().getId());

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
        GuildData guildData = Bot.getGuildData(e.getGuild().getId());

        HashMap<AlertKey, AlertData> alerts = guildData.getAlerts();
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
        GuildData guildData = Bot.getGuildData(e.getGuild().getId());

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


    private ArrayList<Choice> summoner(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        HashMap<String, String> accounts = Bot.getUserData(e.getUser().getId()).getRiotAccounts();
        R4J r4j = RiotHandler.getRiotApi();

        if (accounts == null || accounts.isEmpty()) {
            return choices;
        }
        
        HashMap<String, String> accountNames = new HashMap<>();
        for (String k : accounts.keySet()) {
            String account_id = k;

            LeagueShard shard = LeagueShard.values()[Integer.valueOf(accounts.get(account_id))];
            Summoner summoner = RiotHandler.getSummonerByAccountId(account_id, shard);
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

    private ArrayList<Choice> streamer(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();

        QueryResult streamers = DatabaseHandler.getTwitchSubscriptionsGuild(e.getGuild().getId());
        List<com.github.twitch4j.helix.domain.User> users = TwitchClient.getStreamersById(streamers.arrayColumn("streamer_id"));

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

        else if (e.getFullCommandName().equals("soundboard select")
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

            case "infoaugment":
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
                choices = summoner(e);
                break;
            case "streamer_name":
                choices = streamer(e);
                break;
        }

        e.replyChoices(choices).queue();
    }
}

