package com.safjnest.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.awt.Color;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;

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

    public static List<Container> build(GuildData guild, AlertData alert) {
      List<Container> containers = new ArrayList<>();
      List<ContainerChildComponent> children = new ArrayList<>();

      Section message = Section.of(
        alert.hasMessage() ? Button.primary("alert-modal-public", "modify") : Button.primary("alert-modal-public", "add") ,
        alert.hasMessage() ? TextDisplay.of(alert.getMessage()) : TextDisplay.of("No message is set")
      );

      Section privateMessage = Section.of(
        alert.hasPrivateMessage() ? Button.primary("alert-modal-private", "modify") : Button.primary("alert-modal-private", "add") ,
        alert.hasPrivateMessage() ? TextDisplay.of(alert.getPrivateMessage()) : TextDisplay.of("No private message is set")
      );


      Button sendChannel = Button.primary("alert-send-" + AlertSendType.CHANNEL.name(), AlertSendType.CHANNEL.name());
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

      List<Button> utilityButtons = new ArrayList<>();

      Button toggle = Button.success("alert-disable", "Enabled");
      if (!alert.isEnabled())
        toggle = Button.danger("alert-enable", "Disabled");

      utilityButtons.add(toggle);

      if (alert.getType() == AlertType.LEVEL_UP) {
        ButtonStyle expStyle = guild.isExperienceEnabled() ? ButtonStyle.SUCCESS : ButtonStyle.DANGER;
        Button hasExperience = Button.success("alert-experience", "Gain exp").withStyle(expStyle);
        utilityButtons.add(hasExperience);
      }

      Button delete = Button.danger("alert-delete", " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));
      utilityButtons.add(delete);

      children.add(message);
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(privateMessage);
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(ActionRow.of(sendChannel, sendPrivate, sendBoth));
      children.add(Separator.createDivider(Spacing.LARGE));
      children.add(ActionRow.of(utilityButtons));
      children.add(Separator.createDivider(Spacing.LARGE));

      if (alert.getType() != AlertType.LEAVE)
        children.add(ActionRow.of(getRoleMenu(alert)));
      
      if (alert.getType() != AlertType.LEVEL_UP && alert.getType() != AlertType.REWARD)
        children.add(ActionRow.of(getChannelMenu(alert)));

      containers.add(Container.of(children).withAccentColor(Bot.getColor()));
      if (!alert.isValid(guild)) {
        containers.add(getContainerError(guild, alert));
      }

      if (alert.getType() == AlertType.REWARD)
        containers.add(getRewardButtons(guild, (RewardData) alert));

      containers.add(getAlertTypeContainer(guild, alert));
      
      return containers;
    }


    private static Container getRewardButtons(GuildData guild, RewardData reward) {
      RewardData previous = guild.getLowerReward(reward.getLevel());
      RewardData next = guild.getHigherReward(reward.getLevel());

      Button left = Button.primary("alert-lower", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
      Button center = Button.success("alert-reward-" + reward.getLevel(), "Level: " + reward.getLevel()).asDisabled();
      Button right = Button.primary("alert-higher", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

      if (previous == null) left = left.withStyle(ButtonStyle.DANGER).asDisabled();
      if (next == null) right = right.withStyle(ButtonStyle.DANGER).asDisabled();

      return Container.of(ActionRow.of(left, center, right));
    }


    private static Container getContainerError(GuildData guild, AlertData alert) {
      String errors = "";

      if (alert.getChannelId() == null && alert.getType() != AlertType.LEVEL_UP)
        errors += "Channel is missing\n";

      if (!alert.hasMessage() && !alert.hasPrivateMessage())
        errors += "A message is missing\n";

      if (!alert.isEnabled())
        errors += "Alert is disabled\n";

      if (alert.getType() == AlertType.LEVEL_UP && !guild.isExperienceEnabled()) 
        errors += "The experiece is disabled for this guild";

      return Container.of(TextDisplay.of(errors)).withAccentColor(Color.RED);

    }

    private static Container getAlertTypeContainer(GuildData guild, AlertData alert) {
      List<Button> buttons = new ArrayList<>();
      for (AlertType type : AlertType.values()) {
        if (type == AlertType.TWITCH) continue;

        AlertData buttonAlert = guild.getAlert(type);

        int id = buttonAlert != null ? buttonAlert.getID() : 0;
        Button button = Button.primary("alert-type-" + type + "-" + id , type.getName());
        
        ButtonStyle style = buttonAlert == null ? ButtonStyle.SECONDARY : (alert.getType() == type ? ButtonStyle.SUCCESS : ButtonStyle.PRIMARY);
        button = button.withStyle(style);
        if (alert.getType() == type) button = button.asDisabled();
        buttons.add(button);
      }


      Collections.reverse(buttons);
      return Container.of((ActionRow.of(buttons))).withAccentColor(Bot.getColor());
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

    public static Container getEmptyAlert(AlertType type) {
      Button create = Button.success("alert-type-" + type.name() + "-0", "Create");
      TextDisplay display = TextDisplay.of("This guild does not have a " + type.getDescription().toLowerCase() + ".");
      return Container.of(Section.of(create, display)).withAccentColor(Color.red);
    }
}
