package com.safjnest.core.events;


import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.util.AlertMessage;
import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.SpotifyMessageType;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;

  public class EventFockingComponentsFockingHandlerFockingDotJava extends ListenerAdapter {

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
        default:
          break;
      }
    }


    private void spotify(GenericComponentInteractionCreateEvent event, String innerType, String args) {
      String currentType = "";
      int currentIndex = 0;
      String userId = event.getUser().getId();

      Container buttonContainer = event.getMessage().getComponents().get(1).asContainer();

      for (Component component : buttonContainer.getComponents()) {
        if (component instanceof ActionRow actionRow) {
          for (Component child : actionRow.getComponents()) {
            if (child instanceof Button button) {
              if (button.getCustomId().startsWith("spotify-type-") && button.getStyle() == ButtonStyle.SUCCESS) {
                currentType = button.getCustomId().split("-")[2];
              } else if (button.getCustomId().startsWith("spotify-center-")) {
                currentIndex = Integer.parseInt(button.getCustomId().split("-")[2]);
                userId = button.getCustomId().split("-")[3];
              }
            }
          }
        }
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
          currentType = args;
          currentIndex = 0;
          break;
          
        default:
          break;
      }
      event.getMessage().editMessageComponents(SpotifyMessage.build(userId, SpotifyMessageType.valueOf(currentType.toUpperCase()), currentIndex))
          .useComponentsV2()
          .queue();
    }


    private void alert(GenericComponentInteractionCreateEvent event, String innerType, String args) {
      String alertId = "";

      Container buttonContainer = event.getMessage().getComponents().get(1).asContainer();

      for (Component component : buttonContainer.getComponents()) {
        if (component instanceof ActionRow actionRow) {
          for (Component child : actionRow.getComponents()) {
            if (child instanceof Button button) {
              if (button.getCustomId().startsWith("alert-type-") && button.getStyle() == ButtonStyle.SUCCESS) {
                alertId = button.getCustomId().split("-")[3];
              }
            }
          }
        }
      }
      System.out.println(innerType);
      AlertData alert = GuildCache.getGuild(event.getGuild()).getAlertByID(alertId);
      switch (innerType) {
        case "send":
          event.deferEdit().queue();
          alert.setSendType(AlertSendType.valueOf(args.toUpperCase()));
          break;
        case "modal":
              TextInput streamerInput = TextInput.create("alert-message", "Streamer name", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Hi #user")
                    .build();


                Modal modal = Modal.create("alert-" + alertId, "Modify Alert message")
                        .addComponents(ActionRow.of(streamerInput))
                        .build();

                event.replyModal(modal).queue();
          break;      
        default:
          break;
      }
      event.getMessage().editMessageComponents(AlertMessage.build(alert))
          .useComponentsV2()
          .queue();
    }
}
