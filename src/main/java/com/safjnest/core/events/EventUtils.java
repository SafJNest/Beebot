package com.safjnest.core.events;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Unmodifiable;

import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
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

   @SuppressWarnings({ "unchecked"})
    public @Unmodifiable static List<Button> getButtons(List<MessageTopLevelComponentUnion> components) {
      Stream var10000 = ComponentIterator.createStream(components);
      Objects.requireNonNull(Button.class);
      var10000 = var10000.filter(Button.class::isInstance);
      Objects.requireNonNull(Button.class);
      return (List)var10000.map(Button.class::cast).collect(Collectors.toList());
   }

   @SuppressWarnings({ "unchecked"})
   public @Unmodifiable static List<EntitySelectMenu> getChannelMenu(List<MessageTopLevelComponentUnion> components) {
     Stream var10000 = ComponentIterator.createStream(components);
     Objects.requireNonNull(EntitySelectMenu.class);
     var10000 = var10000.filter(EntitySelectMenu.class::isInstance);
     Objects.requireNonNull(EntitySelectMenu.class);
     return (List)var10000.map(EntitySelectMenu.class::cast).collect(Collectors.toList());
  }

   @SuppressWarnings({ "unchecked"})
    public @Unmodifiable static List<StringSelectMenu> getStringSelectMneu(List<MessageTopLevelComponentUnion> components) {
      Stream var10000 = ComponentIterator.createStream(components);
      Objects.requireNonNull(StringSelectMenu.class);
      var10000 = var10000.filter(StringSelectMenu.class::isInstance);
      Objects.requireNonNull(StringSelectMenu.class);
      return (List)var10000.map(StringSelectMenu.class::cast).collect(Collectors.toList());
   }

   public static Button getButtonById(ButtonInteractionEvent event, String id) {
      return (Button)getButtons(event).stream().filter((it) -> {
         return id.equals(it.getCustomId());
      }).findFirst().orElse((Button)null);
   }

   public static Button getButtonById(List<MessageTopLevelComponentUnion> components, String id) {
      return (Button)getButtons(components).stream().filter((it) -> {
         return id.equals(it.getCustomId());
      }).findFirst().orElse((Button)null);
   }

   public static Button getButtonByPrefix(ButtonInteractionEvent event, String prefix) {
    return (Button)getButtons(event).stream().filter((it) -> {
      return it.getCustomId().startsWith(prefix);
    }).findFirst().orElse((Button)null);
   }

}
