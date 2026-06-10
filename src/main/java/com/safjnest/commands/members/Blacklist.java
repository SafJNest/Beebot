package com.safjnest.commands.members;

import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.Builder;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class Blacklist extends SlashCommand{

    public Blacklist(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();


        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply().addComponents(getMessage(GuildCache.getGuildOrPut(event.getGuild()))).useComponentsV2().queue();
    }

    public static List<Container> getMessage(GuildData guild) {
      Color channelColor = Color.RED;
      Builder builder = EntitySelectMenu.create("blacklist-channel", SelectTarget.CHANNEL).setPlaceholder("Select Channel").setChannelTypes(ChannelType.TEXT).setMaxValues(1);
      if (guild.getBlackChannelId() != null && !guild.getBlackChannelId().isEmpty() && !guild.getBlackChannelId().contains("null")) {
        channelColor = Bot.getColor();
        builder.setDefaultValues(DefaultValue.channel(guild.getBlackChannelId()));
      }
      builder.build();

      List<ContainerChildComponent> children = new ArrayList<>();

      Section threshold = Section.of(
        Button.primary("blacklist-threshold", "Change"),
        TextDisplay.of("Threshold: " + guild.getBlacklistData().getThreshold())
      );

      Section toggle = Section.of(
        guild.getBlacklistData().isBlacklistEnabled() ? Button.success("blacklist-toggle", "Enabled") : Button.danger("blacklist-toggle", "Disabled") ,
        guild.getBlacklistData().isBlacklistEnabled() ? TextDisplay.of("Experience enabled on this channel") : TextDisplay.of("Experience disabled on this channel")
      );

      children.add(TextDisplay.of("The 'Blacklist' keeps track of banned users across servers.\nSet a minimum ban count, and if it's exceeded, notifications alert the servers with the command enabled."));
      children.add(Separator.createDivider(Spacing.LARGE));     
      children.add(threshold);
      children.add(Separator.createDivider(Spacing.SMALL));
      children.add(toggle);

      return List.of(
          Container.of(children).withAccentColor(Bot.getColor()),
          Container.of(ActionRow.of(builder.build())).withAccentColor(channelColor)
      );
    }
}
