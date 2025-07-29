package com.safjnest.core.events;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.SpotifyMessageType;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;

  public class EventFockingComponentsFockingHandlerFockingDotJava extends ListenerAdapter {

    @Override
    public void onGenericComponentInteractionCreate(GenericComponentInteractionCreateEvent event) {
      if (!event.getMessage().isUsingComponentsV2()) return;

      String type = event.getComponentId().split("-")[0];
      String innerType = event.getComponentId().split("-", 3)[1];
      String args = event.getComponentId().split("-", 3).length > 2 ? event.getComponentId().split("-", 3)[2] : "";

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
      String currentType = "", newType = "";
      String filters = "";
      int currentIndex = 0;
      String userId = event.getUser().getId();

      Container buttonContainer = event.getMessage().getComponents().get(event.getMessage().getComponents().size() - 1).asContainer();

      List<Button> filterButtons = new ArrayList<>();

      for (Component component : buttonContainer.getComponents()) {
        if (component instanceof ActionRow actionRow) {
          for (Component child : actionRow.getComponents()) {
            if (child instanceof Button button) {
              System.out.println(button.getCustomId() + " " + button.getStyle());
              if (button.getCustomId().startsWith("spotify-type-") && button.getStyle() == ButtonStyle.SUCCESS) {
                currentType = button.getCustomId().split("-")[2];
              } else if (button.getCustomId().startsWith("spotify-center-")) {
                currentIndex = Integer.parseInt(button.getCustomId().split("-")[2]);
                userId = button.getCustomId().split("-")[3];
              } else if (button.getCustomId().startsWith("spotify-filter-") && button.getStyle() == ButtonStyle.SUCCESS) {
                filterButtons.add(button);
              }
            }
          }
        }
      }
      System.out.println(filterButtons);
      System.out.println(updateFilterString("", event, filterButtons));




      
      switch (innerType) {
        case "left":
          if (currentIndex > 0)
            currentIndex -= 5;
          
          break;
        case "right":
          currentIndex += 5;
          break;

        case "type":
          newType = args;
          currentIndex = 0;
          break;
          
        default:
          break;
      }

      event.getMessage().editMessageComponents(SpotifyMessage.build(userId, SpotifyMessageType.valueOf(newType.toUpperCase()), !currentType.isBlank() ? SpotifyMessageType.valueOf(currentType.toUpperCase()) : null, currentIndex))
          .useComponentsV2()
          .queue();
    }

    private static String updateFilterString(String filtersString, GenericComponentInteractionCreateEvent event, List<Button> activeButtons) {
        Map<String, List<String>> filters = new HashMap<>();

        if (filtersString != null && !filtersString.isEmpty()) {
            String[] entries = filtersString.split(";");
            for (String entry : entries) {
                String[] pair = entry.split("=", 2);
                if (pair.length == 2) {
                    String key = pair[0];
                    List<String> values = new ArrayList<>(Arrays.asList(pair[1].split(",")));
                    filters.put(key, values);
                }
            }
        }

        if (event instanceof StringSelectInteraction selectEvent) {
            String customId = selectEvent.getComponentId();
            String[] parts = customId.split("-");

            if (parts.length >= 3 && parts[0].equals("spotify") && parts[1].equals("filter")) {
                String filterKey = parts[2];
                List<String> selected = selectEvent.getSelectedOptions()
                    .stream()
                    .map(SelectOption::getValue)
                    .toList();

                filters.put(filterKey, new ArrayList<>(selected));
            }
        }

        for (Button button : activeButtons) {
            String[] parts = button.getCustomId().split("-");
            if (parts.length < 4 || button.getStyle() != ButtonStyle.SUCCESS) continue;

            String filterKey = parts[2];
            String rawValue = parts[3];

            String mappedValue = switch (rawValue) {
                case "minus" -> "<";
                case "minusEqual" -> "<=";
                case "equal" -> "=";
                case "greater" -> ">";
                case "greaterEqual" -> ">=";
                default -> rawValue;
            };

            filters.put(filterKey, List.of(mappedValue));
        }

        return filters.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .collect(Collectors.joining(";"));
    }
}
