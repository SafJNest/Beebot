package com.safjnest.core.events;



import java.util.List;

import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.SpotifyMessageType;
import com.safjnest.util.spotify.SpotifyTimeRange;

import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

  public class EventComponentHandler extends ListenerAdapter {

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
}
