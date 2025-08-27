package com.safjnest.core.events;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.TrackData;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.sound.Sound;
import com.safjnest.util.AlertMessage;


import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.type.SpotifyMessageType;
import com.safjnest.util.spotify.type.SpotifyTimeRange;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;

  public class EventComponentsHandler extends ListenerAdapter {

    @Override
    public void onGenericComponentInteractionCreate(GenericComponentInteractionCreateEvent event) {
      if (!event.getMessage().isUsingComponentsV2()) return;
      if (event.getComponent().isModalCompatible()) return;

      String type = event.getComponentId().split("-")[0];
      String innerType = event.getComponentId().split("-", 3)[1];
      String args = event.getComponentId().split("-", 3).length > 2 ? event.getComponentId().split("-", 3)[2] : "";


      switch (type) {
        case "alert":
          alert(event, innerType, args);
          return;
        default:
          break;
      }

      event.deferEdit().queue();
      switch (type) {
        case "spotify":
          spotify(event, innerType, args);
          break;
        case "soundboard":
          soundboard(event, innerType, args);
        default:
          break;
      }
    }


    private void spotify(GenericComponentInteractionCreateEvent event, String innerType, String args) {
      List<Object> oldTimeMsgInfo = SpotifyMessage.getMsgInfo(event.getMessage());
      String userId = (String) oldTimeMsgInfo.get(0);
      SpotifyMessageType currentType = (SpotifyMessageType) oldTimeMsgInfo.get(1);
      int currentIndex = (int) oldTimeMsgInfo.get(2);
      SpotifyTimeRange timeRange = (SpotifyTimeRange) oldTimeMsgInfo.get(3);

      if (event instanceof StringSelectInteractionEvent selectEvent) {
        String selectedValue = selectEvent.getValues().get(0);
        timeRange = SpotifyTimeRange.fromApiLabel(selectedValue);
        currentIndex = 0;
      }

      
      switch (innerType) {
        case "left":
          if (currentIndex > 0)
            currentIndex -= 5;
          break;
        case "right":
          currentIndex += 5;
          break;
        case "type":
          currentType = SpotifyMessageType.valueOf(args.toUpperCase());
          currentIndex = 0;
          break;
        default:
          break;
      }

      SpotifyMessage.send(event.getHook(), 
        userId, 
        currentType, 
        currentIndex, 
        timeRange
      );
    }


    private void alert(GenericComponentInteractionCreateEvent event, String innerType, String args) {
      String alertId = "";
      int rewardLevel = 0;
      for (Button button : EventUtils.getButtons(event)) {
        if (button.getCustomId().startsWith("alert-type-") && button.getStyle() == ButtonStyle.SUCCESS) 
          alertId = button.getCustomId().split("-")[3];
        if (button.getCustomId().startsWith("alert-reward-")) 
          rewardLevel = Integer.parseInt(button.getCustomId().split("-")[2]);
      }

      GuildData guild = GuildCache.getGuild(event.getGuild());
      AlertData alert = guild.getAlertByID(alertId);
      AlertType type = alert != null ? alert.getType() : null;

      EntitySelectInteractionEvent entityEvent;
      Modal modal;
      switch (innerType) {
        case "send":
          event.deferEdit().queue();
          alert.setSendType(AlertSendType.valueOf(args.toUpperCase()));
          break;
        case "role":
          event.deferEdit().queue();
          entityEvent = (EntitySelectInteractionEvent) event;
          List<String> roles = new ArrayList<>();
          for (IMentionable entity : entityEvent.getValues()) {
            roles.add(entity.getId());
          }
          alert.setRoles(roles);
          break;
        case "channel":
          event.deferEdit().queue();
          entityEvent = (EntitySelectInteractionEvent) event;
          alert.setAlertChannel(entityEvent.getChannelId());
          break;
        case "modal":
              TextInput messageInput = TextInput.create("alert-message-" + args, "Alert Message, leave blank to remove", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hi #user, welcome in #server")
                    .setRequired(false)
                    .setMaxLength(1500)
                    .build();
                modal = Modal.create("alert-" + alertId, "Modify Alert message")
                        .addComponents(ActionRow.of(messageInput))
                        .build();

                event.replyModal(modal).queue();
          return;      
        case "type":
          event.deferEdit().queue();
          type = AlertType.valueOf(args.split("-")[0].toUpperCase());
          alert = guild.getAlert(type);
          if (alert == null) {
            AlertData newAlertData = new AlertData(event.getGuild().getId(), "", "", null, AlertSendType.CHANNEL, type);
            guild.getAlerts().put(newAlertData.getKey(), newAlertData);
          }
          alert = guild.getAlert(type);
          break;
        case "disable":
          event.deferEdit().queue();
          alert.setEnabled(false);
          break;
        case "enable":
          event.deferEdit().queue();
          alert.setEnabled(true);
          break;
        case "delete":
          event.deferEdit().queue();
          guild.deleteAlert(alert.getType());
          Container delete = Container.of(TextDisplay.of("Alert deleted correctly")).withAccentColor(Color.GREEN);
          event.getMessage().editMessageComponents(delete)
            .useComponentsV2()
            .queue();
          return;
        case "experience":
          event.deferEdit().queue();
          guild.setExpSystem(((Button) event.getComponent()).getStyle() == ButtonStyle.DANGER ? true : false);
          break;
        case "lower":
          event.deferEdit().queue();
          alert = guild.getLowerReward(rewardLevel);
          break;
        case "higher":
          event.deferEdit().queue();
          alert = guild.getHigherReward(rewardLevel);
          break;
        case "createReward":
              TextInput rewardInputLevel = TextInput.create("reward-level", "Select a new reward level", TextInputStyle.SHORT)
                    .setPlaceholder("117")
                    .setMinLength(1)
                    .setRequired(true)
                    .build();
                modal = Modal.create("reward-" + alertId, "Modify Alert message")
                        .addComponents(ActionRow.of(rewardInputLevel))
                        .build();

                event.replyModal(modal).queue();
          return;
        case "temporary":
          event.deferEdit().queue();
          alert.asReward().setTemporary(((Button) event.getComponent()).getStyle() == ButtonStyle.DANGER ? true : false);
          break;
        default:
          break;
      }

      event.getMessage().editMessageComponents(AlertMessage.build(guild, alert))
          .useComponentsV2()
          .queue();
    }

      private void soundboard(GenericComponentInteractionCreateEvent event, String innerType, String args) {
        Guild guild = event.getGuild();
        System.out.println(args);
        String sound_id = innerType.split("\\.")[0];

        List<Button> buttons = EventUtils.getButtons(event).stream()
          .filter(b -> b.getCustomId().startsWith("soundboard-") && !b.getCustomId().endsWith("-stop") && !b.getCustomId().endsWith("-random"))
          .collect(Collectors.toList());
        
        PlayerManager pm = PlayerManager.get();
        switch (innerType) {
            case "stop":
                pm.getGuildMusicManager(guild).getTrackScheduler().stop();
                return;
            case "random":
                int randomIndex = (int) (Math.random() * buttons.size());
                sound_id = buttons.get(randomIndex).getCustomId().split("-")[1].split("\\.")[0];
                break;
            default: 
                break;
        }

        final String id = sound_id;

        MessageComponentTree tree = event.getMessage().getComponentTree();
        for (Button button : buttons) {
          button = button.withStyle(ButtonStyle.PRIMARY);
          if (button.getCustomId().startsWith("soundboard-" + id + "."))
            button = button.withStyle(ButtonStyle.SUCCESS);
          tree = tree.replace(ComponentReplacer.byUniqueId(button.getUniqueId(), button));
        }

        TextChannel textChannel = event.getChannel().asTextChannel();
        AudioChannel audioChannel = event.getMember().getVoiceState().getChannel();

        Sound sound = SoundCache.getSoundById(id);
        String path = sound.getPath();

        final MessageComponentTree finalTree = tree;

        pm.loadItemOrdered(guild, path, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (!guild.getAudioManager().isConnected()) guild.getAudioManager().openAudioConnection(audioChannel);

                sound.increaseUserPlays(event.getMember().getId(), AudioType.SOUNDBOARD);
                track.setUserData(new TrackData(AudioType.SOUNDBOARD));
                pm.getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.SOUNDBOARD);
                event.getHook().editOriginalComponents(finalTree).useComponentsV2().queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {}

            @Override
            public void noMatches() {
                textChannel.sendMessage("File not found").queue();
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                System.out.println("error: " + throwable.getMessage());
            }
        });

    }

}
