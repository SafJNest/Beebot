package com.safjnest.commands.guild;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.events.types.WarningEvent;
import com.safjnest.model.guild.MemberData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Warn extends SlashCommand {

    public Warn(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.userPermissions = new Permission[]{Permission.ADMINISTRATOR};

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User to warn", true),
            new OptionData(OptionType.STRING, "reason", "Reason", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        Member member = PermissionHandler.getMentionedMember(event, event.getArgs().split(" ")[0]);
        String reason = event.getArgs().split(" ")[1];

        MemberData memberData = GuildCache.getGuildOrPut(event.getGuild()).getMemberData(member.getId());
        int warningId = memberData.warn(reason);
        if (warningId != -1) {
            event.reply("Error");
            return;
        } 

        Bot.handleEvent(new WarningEvent(event.getJDA(), event.getResponseNumber(), event.getChannel(), memberData, warningId, reason));
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(member.getEffectiveName() + " has been warned", null, member.getAvatarUrl());
        eb.setColor(Bot.getColor());
        if (!reason.isEmpty()) {
            eb.setDescription("**Reason:** " + reason);
        }


        event.reply(eb.build());
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String reason = event.getOption("reason") == null ? "" : event.getOption("reason").getAsString();
        Member member = event.getOption("user").getAsMember();

        MemberData memberData = GuildCache.getGuildOrPut(event.getGuild()).getMemberData(member.getId());
        
        int warningId = memberData.warn(reason);
        if (warningId == -1) {
            event.reply("Error").queue();
            return;
        } 

        Bot.handleEvent(new WarningEvent(event.getJDA(), event.getResponseNumber(), event.getChannel(), memberData, warningId, reason));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(member.getEffectiveName() + " has been warned", null, member.getAvatarUrl());
        eb.setColor(Bot.getColor());
        if (!reason.isEmpty()) {
            eb.setDescription("**Reason:** " + reason);
        }


        event.replyEmbeds(eb.build()).queue();
    }
}