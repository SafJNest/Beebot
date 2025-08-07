package com.safjnest.util;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.Builder;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.entities.channel.ChannelType;

public class AlertMessage {

    public static List<Container> build(AlertData alert) {
      List<ContainerChildComponent> children = new ArrayList<>();


      //TextInput input = TextInput.create("alert-message-" + alert.getID(), "Default Message", TextInputStyle.PARAGRAPH).build();

      Section message = Section.of(
        Button.primary("alert-modal-public", "modify"),
        TextDisplay.of(alert.getMessage())
      );

      Section privateMessage = Section.of(
        alert.hasPrivateMessage() ? Button.primary("alert-modal-private" + alert.getID(), "modify") : Button.primary("alert-modal-private" + alert.getID(), "add") ,
        alert.hasPrivateMessage() ? TextDisplay.of(alert.getPrivateMessage()) : TextDisplay.of("No private message is set")
      );


      Button sendChannel = Button.primary("alert-send-" + AlertSendType.CHANNEL, AlertSendType.CHANNEL.name());
      Button sendPrivate = Button.primary("alert-send-" + AlertSendType.PRIVATE.name(), AlertSendType.PRIVATE.name());
      Button sendBoth = Button.primary("alert-send-" + AlertSendType.BOTH.name(), AlertSendType.BOTH.name());

      switch (alert.getSendType()) {
        case CHANNEL:
          sendChannel = sendChannel.withStyle(ButtonStyle.SUCCESS).asDisabled();
          break;
        case PRIVATE:
          sendPrivate = sendPrivate.withStyle(ButtonStyle.SUCCESS).asDisabled();
          break;
        case BOTH:
          sendBoth = sendBoth.withStyle(ButtonStyle.SUCCESS).asDisabled();
        default:
          break;
      }



      children.add(message);
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(privateMessage);
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(ActionRow.of(sendChannel, sendPrivate, sendBoth));
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(ActionRow.of(getRoleMenu(alert)));
      children.add(ActionRow.of(getChannelMenu(alert)));

      Button welcome = Button.success("alert-type-" + AlertType.WELCOME + "-" + alert.getID(), "Welcome");

      return List.of(Container.of(children), Container.of(ActionRow.of(welcome)));
    }

    private static EntitySelectMenu getRoleMenu(AlertData alert) {
      List<DefaultValue> values = new ArrayList<>();

      if (alert.getRolesAsArray().length > 0) {
        for (String role : alert.getRolesAsArray()) {
          values.add(DefaultValue.role(role));
        }
      }

      Builder builder = EntitySelectMenu.create("alert-role-" + alert.getID(), SelectTarget.ROLE).setPlaceholder("Select roles").setMaxValues(25);
      if (values.size() > 0) builder.setDefaultValues(values);

      return builder.build();
    }

    private static EntitySelectMenu getChannelMenu(AlertData alert) {
      Builder builder = EntitySelectMenu.create("alert-channel-" + alert.getID(), SelectTarget.CHANNEL).setPlaceholder("Select Channel").setChannelTypes(ChannelType.TEXT).setMaxValues(1);
      if (alert.getChannelId() != null && !alert.getChannelId().isEmpty()) builder.setDefaultValues(DefaultValue.channel(alert.getChannelId()));

      return builder.build();
    }
}
