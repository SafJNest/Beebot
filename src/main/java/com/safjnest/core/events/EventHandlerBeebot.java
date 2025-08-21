package com.safjnest.core.events;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import com.safjnest.core.Bot;
import com.safjnest.core.events.interfaces.BeebotListenerAdapter;
import com.safjnest.core.events.types.WarningEvent;
import com.safjnest.model.guild.AutomatedAction;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.MemberData;
import com.safjnest.sql.BotDB;
import com.safjnest.util.AutomatedActionTimer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import com.safjnest.core.cache.managers.GuildCache;

/**
 * Guess who's back, back again
 */
public class EventHandlerBeebot extends BeebotListenerAdapter {

    @Override
    public void onWarning(WarningEvent event) {
        Guild guild = event.getGuild();
        GuildData guildData = GuildCache.getGuildOrPut(guild);

        TextChannel channel = event.getChannel().asTextChannel();

        Member member = event.getGuild().getMemberById(event.getMemberData().getUserId());
        MemberData memberData = event.getMemberData();

        if (guildData.getActions().isEmpty()) return;

        for (AutomatedAction action : guildData.getActions()) {
            if (!action.canExecute(memberData.getId())) continue;

            LocalDateTime dateTime = LocalDateTime.now().plusSeconds(action.getActionTime());
            String automatedActionExpiringId = BotDB.insertAutomatedActionExpiring(memberData.getId(), action.getId(), dateTime.toEpochSecond(ZoneOffset.UTC) * 1000);
            
            Runnable onSuccess = () -> {
                if (action.getActionTime() != 0) {
                    AutomatedActionTimer.scheduleAATask(dateTime, automatedActionExpiringId, action.getId(), event.getMemberData().getUserId(), event.getGuild().getId());
                }
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle(":skull: User got punished :skull:");
                eb.setDescription(member.getAsMention() + " got punished after exceeding the infractions limit of " + action.getInfractions() + " warnings.\n" + action.getActionMessage());
                eb.setColor(Bot.getColor());
                eb.setAuthor(member.getEffectiveName(), null, member.getUser().getAvatarUrl());
                channel.sendMessageEmbeds(eb.build()).queue();
            };

            switch (action.getAction()) {
                case AutomatedAction.MUTE:
                    event.getGuild().addRoleToMember(member, guild.getRoleById(action.getRoleId())).queue(
                        success -> {
                            onSuccess.run();
                        },
                        failure -> failure.printStackTrace()
                    );
                
                    break;
                case AutomatedAction.KICK:
                    event.getGuild().kick(member).queue(
                        success -> onSuccess.run(),
                        failure -> failure.printStackTrace()
                    );
                    break;
                case AutomatedAction.BAN:
                    event.getGuild().ban(member, 0, TimeUnit.SECONDS).reason(event.getReason()).queue(
                        success -> onSuccess.run(),
                        failure -> failure.printStackTrace()
                    );
                    break;
                default:
                    break;
            }
            break;
        }
    } 
}
