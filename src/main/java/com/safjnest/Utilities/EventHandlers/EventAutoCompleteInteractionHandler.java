package com.safjnest.Utilities.EventHandlers;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.safjnest.Bot;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TTSVoices;
import com.safjnest.Utilities.Guild.GuildData;
import com.safjnest.Utilities.Guild.Alert.AlertData;
import com.safjnest.Utilities.Guild.Alert.AlertKey;
import com.safjnest.Utilities.Guild.Alert.AlertType;
import com.safjnest.Utilities.Guild.Alert.RewardData;
import com.safjnest.Utilities.LOL.AugmentData;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class EventAutoCompleteInteractionHandler extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
        GuildData guildData = Bot.getGuildData(e.getGuild().getId());
        
        ArrayList<Choice> choices = new ArrayList<>();
        String name = e.getName();

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

        else if (e.getFullCommandName().equals("customizesound"))
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
        

        switch (name) {
            case "play":
                if (e.getFocusedOption().getValue().equals("")) {
                    for (ResultRow sound : DatabaseHandler.getGuildRandomSound(e.getGuild().getId()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                } else {
                    for (ResultRow sound : DatabaseHandler.getFocusedGuildSound(e.getGuild().getId(),
                            e.getFocusedOption().getValue()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                }

                break;
            case "user_sound":
                if (e.getFocusedOption().getValue().equals("")) {
                    for (ResultRow sound : DatabaseHandler.getUserRandomSound(e.getGuild().getId()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                } else {
                    for (ResultRow sound : DatabaseHandler.getFocusedUserSound(e.getGuild().getId(),
                            e.getFocusedOption().getValue()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                }

                break;
            case "help":
                List<Command> allCommands = e.getJDA().retrieveCommands().complete();
                if (e.getFocusedOption().getValue().equals("")) {
                    Collections.shuffle(allCommands);
                    for (int i = 0; i < 10; i++)
                        choices.add(new Choice(allCommands.get(i).getName(), allCommands.get(i).getName()));
                } else {
                    for (Command c : allCommands) {
                        if (c.getName().startsWith(e.getFocusedOption().getValue()))
                            choices.add(new Choice(c.getName(), c.getName()));
                    }
                }
                break;

            case "champion":
                List<String> champions = Arrays.asList(RiotHandler.getChampions());
                if (e.getFocusedOption().getValue().equals("")) {
                    Collections.shuffle(champions);
                    for (int i = 0; i < 10; i++)
                        choices.add(new Choice(champions.get(i), champions.get(i)));
                } else {
                    int max = 0;
                    for (int i = 0; i < champions.size() && max < 10; i++) {
                        if (champions.get(i).toLowerCase().startsWith(e.getFocusedOption().getValue().toLowerCase())) {
                            choices.add(new Choice(champions.get(i), champions.get(i)));
                            max++;
                        }
                    }
                }
                break;

            case "infoaugment":
                List<AugmentData> augments = RiotHandler.getAugments();
                if (e.getFocusedOption().getValue().equals("")) {
                    Collections.shuffle(augments);
                    for (int i = 0; i < 10; i++)
                        choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                } else {
                    int max = 0;
                    if (e.getFocusedOption().getValue().matches("\\d+")) {
                        for (int i = 0; i < augments.size() && max < 10; i++) {
                            if (augments.get(i).getId().startsWith(e.getFocusedOption().getValue())) {
                                choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                                max++;
                            }
                        }
                    } else {
                        for (int i = 0; i < augments.size() && max < 10; i++) {
                            if (augments.get(i).getName().toLowerCase().startsWith(e.getFocusedOption().getValue().toLowerCase())) {
                                choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                                max++;
                            }
                        }
                    }
                }
                break;

            case "soundboard_select":
                if (e.getFocusedOption().getValue().equals("")) {
                    for (ResultRow sound : DatabaseHandler.getRandomSoundboard(e.getGuild().getId()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                } else {
                    for (ResultRow sound : DatabaseHandler.getFocusedSoundboard(e.getGuild().getId(), e.getFocusedOption().getValue()))
                        choices.add(new Choice(sound.get("name"), sound.get("id")));
                }
                break;
            case "sound_remove":
                if (e.getOption("name") == null)
                    return;
                String soundboardId = e.getOption("name").getAsString();
                if (e.getFocusedOption().getValue().equals("")) {
                    for (ResultRow sound : DatabaseHandler.getSoundsFromSoundBoard(soundboardId))
                        choices.add(new Choice(sound.get("sound.name"), sound.get("soundboard_sounds.sound_id")));
                } else {
                    for (ResultRow sound : DatabaseHandler.getFocusedSoundFromSounboard(soundboardId, e.getFocusedOption().getValue()))
                        choices.add(new Choice(sound.get("s.name"), sound.get("s.id")));
                }
                break;

            case "greet":
                if (e.getFocusedOption().getValue().equals("")) {
                    for (ResultRow greet : DatabaseHandler.getlistGuildSounds(e.getGuild().getId(), 25))
                        choices.add(new Choice(greet.get("name"), greet.get("id")));
                } else {
                    for (ResultRow greet : DatabaseHandler.getFocusedListUserSounds(e.getUser().getId(), e.getGuild().getId(), e.getFocusedOption().getValue()))
                        choices.add(new Choice(greet.get("name") + " (" + greet.get("id") + ")", greet.get("id")));
                }
                break;
            case "tts":
                List<String[]> voices = TTSVoices.getVoiceArray();
                if (e.getFocusedOption().getValue().equals("")) {
                    Collections.shuffle(voices);
                    for (int i = 0; i < 10; i++)
                        choices.add(new Choice(voices.get(i)[0] + " - " + voices.get(i)[1], voices.get(i)[1]));
                } else {
                    for (String[] voice : voices) {
                        if ((voice[0] + " " + voice[1]).toLowerCase().contains(e.getFocusedOption().getValue().toLowerCase()))
                            choices.add(new Choice(voice[0] + " - " + voice[1], voice[1]));
                    }
                }
                break;
            case "jumpto":
                List<AudioTrack> queue = PlayerManager.get().getGuildMusicManager(e.getGuild()).getTrackScheduler().getQueue();
                if (e.getFocusedOption().getValue().equals("")) {
                    for (int i = 0; i < queue.size() && i < 10; i++)
                        choices.add(new Choice("[" + (i + 1) + "] " + queue.get(i).getInfo().title, String.valueOf(i + 1)));
                } else {
                    String query = e.getFocusedOption().getValue().toLowerCase();

                    for (int i = 0, max = 0; i < queue.size() && max < 10; i++) {
                        String title = "[" + (i + 1) + "] " + queue.get(i).getInfo().title;
                        if (title.toLowerCase().contains(query)) {
                            choices.add(new Choice(title, String.valueOf(i + 1)));
                            max++;
                        }
                    }
                }
                break;
            case "alert_role":
                AlertData alert = guildData.getAlert(AlertType.WELCOME);
                if (alert != null && alert.getRoles() != null) {
                    HashMap<Integer, String> alertRoles = alert.getRoles();
                    List<Role> roles = new ArrayList<>();
                    for (Role r : e.getGuild().getRoles()) {
                        if (alertRoles.containsValue(r.getId()))
                            roles.add(r);
                    }

                    Collections.shuffle(roles);
                    if (e.getFocusedOption().getValue().equals("")) {
                        for (int i = 0; i < roles.size() && i < 10; i++)
                            choices.add(new Choice(roles.get(i).getName(), roles.get(i).getId()));
                    } else {
                        for (Role role : roles) {
                            if (role.getName().toLowerCase().contains(e.getFocusedOption().getValue().toLowerCase()))
                                choices.add(new Choice(role.getName(), role.getId()));
                        }

                    }
                }
                break;
            case "rewards_level":
                HashMap<AlertKey, AlertData> alerts = guildData.getAlerts();
                List<String> levels = new ArrayList<>();
                for (AlertData data : alerts.values()) {
                    if (data.getType() == AlertType.REWARD)
                        levels.add(String.valueOf(((RewardData) data).getLevel()));
                }
                if (e.getFocusedOption().getValue().equals("")) {
                    Collections.shuffle(levels);
                    for (int i = 0; i < levels.size() && i < 10; i++)
                        choices.add(new Choice(levels.get(i), levels.get(i)));
                } else {
                    for (String level : levels) {
                        if (level.startsWith(e.getFocusedOption().getValue()))
                            choices.add(new Choice(level, level));
                    }
                }
                break;
            case "reward_roles":
                if (e.getOption("reward_level") == null)
                    return;
                String rewardLevel = e.getOption("reward_level").getAsString();
                RewardData reward = guildData.getAlert(AlertType.REWARD, Integer.parseInt(rewardLevel));
                if (reward != null && reward.getRoles() != null) {
                    HashMap<Integer, String> rewardRoles = reward.getRoles();
                    List<Role> roles = new ArrayList<>();
                    for (Role r : e.getGuild().getRoles()) {
                        if (rewardRoles.containsValue(r.getId()))
                            roles.add(r);
                    }
                    Collections.shuffle(roles);
                    if (e.getFocusedOption().getValue().equals("")) {
                        for (int i = 0; i < roles.size() && i < 10; i++)
                            choices.add(new Choice(roles.get(i).getName(), roles.get(i).getId()));
                    } else {
                        for (Role role : roles) {
                            if (role.getName().toLowerCase().contains(e.getFocusedOption().getValue().toLowerCase()))
                                choices.add(new Choice(role.getName(), role.getId()));
                        }
                    }
                }
                break;
            case "personal_summoner":
                QueryResult accounts = DatabaseHandler.getLolAccounts(e.getUser().getId());
                R4J r4j = RiotHandler.getRiotApi();
                if (accounts.isEmpty()) {
                    return;
                }

                HashMap<String, String> accountNames = new HashMap<>();
                for (ResultRow account : accounts) {
                    LeagueShard shard = LeagueShard.values()[account.getAsInt("league_shard")];
                    Summoner summoner = RiotHandler.getSummonerByAccountId(account.get("account_id"), shard);
                    RiotAccount riotAccount = r4j.getAccountAPI().getAccountByPUUID(shard.toRegionShard(), summoner.getPUUID());
                    accountNames.put(account.get("account_id"), riotAccount.getName() + "#" + riotAccount.getTag());
                }

                if (e.getFocusedOption().getValue().equals("")) {
                    accountNames.forEach((k, v) -> choices.add(new Choice(v, k)));  
                } else {
                    accountNames.forEach((k, v) -> {
                        if (v.toLowerCase().contains(e.getFocusedOption().getValue().toLowerCase()))
                            choices.add(new Choice(v, k));
                    });
                }


                break;
        }

        e.replyChoices(choices).queue();
    }
}

