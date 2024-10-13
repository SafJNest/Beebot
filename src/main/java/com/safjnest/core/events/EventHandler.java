package com.safjnest.core.events;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;
import com.safjnest.core.Bot;
import com.safjnest.core.chat.ChatHandler;
import com.safjnest.model.UserData;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertType;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

/**
 * This class handles all events that could occur during the listening:
 * <ul>
 * <li>On update of a voice channel (to make the bot leave an empty voice
 * channel)</li>
 * <li>On join of a user (to make the bot welcome the new member)</li>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class EventHandler extends ListenerAdapter {

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e) {
        Guild guild = e.getGuild();
        User self = e.getJDA().getSelfUser();
        User userJoined = e.getMember().getUser();
        

        AudioChannel channelJoined = e.getChannelJoined();
        AudioChannel channelLeft = e.getChannelLeft();
        AudioChannel connectChannel = guild.getAudioManager().getConnectedChannel();
        VoiceChannel afkChannel = guild.getAfkChannel();

        if(e.getMember().getId().equals(self.getId()) && channelJoined == null)
            Functions.handleBotLeave(guild);

        if (Functions.isBotAlone(connectChannel, channelLeft))
            Functions.handleBotLeave(guild);
        
        if (channelJoined != null && (afkChannel != null && channelJoined.getIdLong() != afkChannel.getIdLong()) &&
            (connectChannel == null || channelJoined.getId().equals(connectChannel.getId())) && !userJoined.isBot()) {
            Functions.handleGreetSound(channelJoined, userJoined, guild);
        }
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot())
            return;

        if (e.isFromType(ChannelType.PRIVATE)) {
            e.getJDA().getTextChannelById("1294052017815158804").sendMessage(e.getAuthor().getName() + "(" + e.getAuthor().getId() + "): " + e.getMessage().getContentDisplay()).queue();
            return;
        }

        GuildData guildData = Bot.getGuildData(e.getGuild().getId());
        UserData userData = Bot.getUserData(e.getAuthor().getId());

        Functions.handleAlias(guildData, userData, e);
        Functions.handleExperience(guildData, e);

        ChatHandler.relayMessage(e);
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event){
        Bot.getGuildData(event.getGuild().getId());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        Functions.handleCustomCommand(commandName, event);
        Functions.updateCommandStatitics(event);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User newGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(newGuy, guild, AlertType.WELCOME);
        Functions.handleBlacklist(newGuy, guild);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event){
        User oldGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(oldGuy, guild, AlertType.LEAVE);
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        User theGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleBlacklistAlert(theGuy, guild);
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        User theGuy = event.getUser();
        DatabaseHandler.deleteBlacklist(event.getGuild().getId(), theGuy.getId());
    }

    @Override
    public void onGuildMemberUpdateBoostTime(GuildMemberUpdateBoostTimeEvent event) {
        User theGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(theGuy, guild, AlertType.BOOST);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event){
        if(!event.getChannelType().isAudio()){
            Functions.handleChannelDeleteAlert(event.getGuild(), event.getChannel().getId());
        }
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event){
        Functions.handleRoleDeleteAlert(event.getGuild(), event.getRole().getId());
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("rank-select")) {
            String summonerId = event.getValues().get(0).split("#")[0];
            String platform =  event.getValues().get(0).split("#")[1];
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = LeagueHandler.getSummonerBySummonerId(summonerId, LeagueShard.valueOf(platform));
            event.deferEdit().setEmbeds(LeagueMessage.getSummonerEmbed(s).build()).setComponents(LeagueMessage.getSummonerButtons(s, platform)).queue();
        }
        else if (event.getComponentId().equals("opgg-select")) {
            event.deferEdit().queue();
            String gameId = event.getValues().get(0);
            String platform =  event.getValues().get(0).split("_")[0];
            String accountId =  event.getValues().get(0).split("#")[1];

            no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = LeagueHandler.getSummonerByAccountId(accountId, LeagueShard.valueOf(platform));
            
            LeagueShard shard = LeagueShard.valueOf(platform);
            LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), gameId);
            event.getMessage().editMessageEmbeds(LeagueMessage.getOpggEmbedMatch(s, match).build()).setComponents(event.getMessage().getComponents()).queue(); 
        }
    }
}
