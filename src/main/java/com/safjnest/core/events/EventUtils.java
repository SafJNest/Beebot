package com.safjnest.core.events;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Unmodifiable;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.utils.ComponentIterator;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;

public class EventUtils {

    @SuppressWarnings({ "unchecked" })
    public @Unmodifiable static List<Button> getButtons(GenericComponentInteractionCreateEvent event) {
      Stream var10000 = ComponentIterator.createStream(event.getMessage().getComponents());
      Objects.requireNonNull(Button.class);
      var10000 = var10000.filter(Button.class::isInstance);
      Objects.requireNonNull(Button.class);
      return (List)var10000.map(Button.class::cast).collect(Collectors.toList());
    }  

    @SuppressWarnings({ "unchecked"})
    public @Unmodifiable static List<Button> getButtons(ButtonInteractionEvent event) {
      Stream var10000 = ComponentIterator.createStream(event.getMessage().getComponents());
      Objects.requireNonNull(Button.class);
      var10000 = var10000.filter(Button.class::isInstance);
      Objects.requireNonNull(Button.class);
      return (List)var10000.map(Button.class::cast).collect(Collectors.toList());
   }

    public static Button getButtonById(ButtonInteractionEvent event, String id) {
      return (Button)getButtons(event).stream().filter((it) -> {
         return id.equals(it.getCustomId());
      }).findFirst().orElse((Button)null);
   }

}
