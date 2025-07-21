package com.safjnest.core.events;

import java.io.IOException;
import java.util.List;

import com.safjnest.util.spotify.SpotifyMessage;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class EventFockingComponentsFockingHandlerFockingDotJava  extends ListenerAdapter {

    @Override
    public void onGenericComponentInteractionCreate(GenericComponentInteractionCreateEvent event) {
      if (!event.getMessage().isUsingComponentsV2()) return;

      String type = event.getComponentId().split("-")[0];
      String innerType = event.getComponentId().split("-", 3)[1];
      String args = event.getComponentId().split("-", 3).length > 2 ? event.getComponentId().split("-", 3)[2] : "";

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

      Container buttonContainer = event.getMessage().getComponents().get(1).asContainer();

      for (Component component : buttonContainer.getComponents()) {
        if (component instanceof ActionRow actionRow) {
          for (Component child : actionRow.getComponents()) {
            if (child instanceof Button button) {
              System.out.println(button.getCustomId());
              if (button.getCustomId().startsWith("spotify-type-") && button.getStyle() == ButtonStyle.SUCCESS) {
                currentType = button.getCustomId().split("-")[2];
              } else if (button.getCustomId().startsWith("spotify-center-")) {
                currentIndex = Integer.parseInt(button.getCustomId().split("-")[2]);
              }
            }
          }
        }
      }

      

      System.out.println(innerType + " " + currentType + " " + currentIndex);
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
        default:
          break;
      }
      System.out.println(innerType + " " + currentType + " " + currentIndex);
      try {
        event.editComponents(List.of(SpotifyMessage.getMainContent(currentType, currentIndex), SpotifyMessage.getButtonComponents(currentType, currentIndex))).useComponentsV2(true).queue();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
}
